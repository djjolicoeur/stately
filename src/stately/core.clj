(ns stately.core
  (:require [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [stately.machine.state-machine :as sm]
            [stately.graph.directed-graph :as dg]
            [stately.graph.node :as node]
            [stately.components.data-store :as ds]
            [stately.components.state-store :as ss]
            [stately.components.executor :as exec]))


;;Defines the core protocols necessary to run the stately machine
(defprotocol IStatelyCore
  (state-machine [this] "Something that implements IStateMachine")
  (handle-state-fn [this] "multi fn to handle state transitions")
  (data-fn [this] "fn to get data")
  (state-store [this] "Something that implements the state store protocols")
  (data-store [this] "Something that implements the data store protocols")
  (executor [this] "Something that implements the Executor protocols")
  (min-scheduler-interval [this] "minimum interval to schedule next job in ms")
  (reschedule-delta-max [this] "max age of a job to be loaded on bootstrap in ms."))



;; Protocol that defines everything needed to build a
;; Stately component
(defprotocol IStately
  (data [this] [this event] "get data to deliver from app state/DB")
  (input [this event] "Send input to State Machine")
  (expire [this] "expire current state")
  (handle-state [this new-state] "handle side effects of new state")
  (get-state [this] "look up current state")
  (persist-state [this new-state] "save state of workflow")
  (schedule-executor [this new-state] "schedule timeout")
  (reschedule [this] "reschedule a state with the executor"))


(declare executable defer-handle-state)

(defn- now []
  (java.util.Date.))

(defn- reject-state
  [state]
  (-> state
      (dissoc :next-in)
      (assoc  :state :rej
              :reject? true
              :tx (now))))

;; On of the simplest implementations I can think of to illustrate
;; the major points of what I'm trying to do with this project.
;; Here we have
;; - state-machine - the definition of a state machine
;; - component - a StatelyComponent
;; - id - a unique identifier for a particular instance of state
;; - ref - reference into the data-store
(defrecord SimpleStately [core ref]
  IStately
  (get-state [this] (ss/get-state (state-store core) ref))
  (persist-state [this new-state]
    (log/info :at ::persist-state :new-state new-state)
    (if (:accept? new-state)
      (ss/evict-state! (state-store core) ref)
      (ss/persist-state! (state-store core) ref new-state)))
  (data [this]
    ((data-fn core) (ds/get-data (data-store core)) ref))
  (data [this event]
    ((data-fn core) (ds/get-data (data-store core)) ref))
  (handle-state [this new-state]
    ((handle-state-fn core) core (:state new-state) ref (data this)))
  (schedule-executor [this new-state]
    (exec/schedule (executor core)
                   (executable core ref new-state)
                   (max (min-scheduler-interval core)
                        (:next-in new-state))))
  (input [this event]
    (log/debug :at ::input :msg "Received Event" :event event)
    (let [state (get-state this)
          data (data this event)
          new-state (sm/advance (state-machine core) :input
                                (:state state) data event)]
      (log/debug :at ::input
                 :msg "State Transition"
                 :state state
                 :new-state new-state)
      (persist-state this new-state)
      (defer-handle-state this new-state)
      (when (:next-in new-state)
        (schedule-executor this new-state))))
  (expire [this]
    (log/info :at ::expire :msg "Expiring Job" :ref ref)
    (let [state (get-state this)
          data (data this)
          new-state (sm/advance (state-machine core)
                                :expire (:state state) data)]
      (log/info :at ::expire :expired-to new-state)
      (persist-state this new-state)
      (defer-handle-state this new-state)
      (when (:next-in new-state)
        (schedule-executor this new-state))))
  (reschedule [this]
    (let [state (get-state this)
          ref-data (data this)
          now (.getTime (java.util.Date.))
          tx (.getTime (:tx state))
          delta (- now tx)
          min-next-in (min-scheduler-interval core)
          max-job-delta (if (reschedule-delta-max core)
                          (- (reschedule-delta-max core))
                          (- (* 24 60 60 1000)))]
      (log/info :at ::reschedule
                :data? (boolean ref-data)
                :state state
                :delta delta
                :min-next-in min-next-in
                :max-job-delta max-job-delta)
      (when (and ref-data
                 (not (:reject? state))
                 (not (= :rej (:state state))) ;;handle legacy rejections
                 (:next-in state))
        (let [next-in (- (:next-in state) delta)]
          (log/info :at ::reschedule
                    :msg "Rescheduling Job"
                    :next-in next-in
                    :min-next-in min-next-in)
          (if (<= next-in min-next-in)
            (if (> next-in max-job-delta)
              (expire this)
              (persist-state this (reject-state state)))
            (exec/schedule (executor core)
                           (executable core ref state)
                           (max min-next-in next-in))))))))



(defn- defer-handle-state
  [stately new-state]
  (log/info :at ::defer-handle-state :new-state new-state)
  (let [d (d/deferred)]
    (-> new-state
        (d/chain d
                 #(future
                    (do
                      (log/info :at ::execute-handle-state :state new-state)
                      (handle-state stately %)))
                 #(when-not % (persist-state stately (reject-state new-state))))
        (d/catch Exception
            #(do
               (log/error :at ::defer-handle-state
                          :msg "Exception Caught in State Handler"
                          :new-state new-state
                          :stately stately)
               (persist-state stately (reject-state new-state)))))))


(defn executable [core ref state]
  (fn []
    (log/info :at ::executable :msg "Executing Scheduled Job" :ref ref)
    (let [current (->SimpleStately core ref)
          new-state (get-state current)]
      (log/debug :at ::executable :new-state new-state :old-state state)
      (when (= state new-state)
        (expire current)))))

(defn stately [core ref]
  (->SimpleStately core ref))

(defn bootstrap-executor [core]
  (let [refs (ss/all-refs (state-store core))]
    (doseq [ref refs]
      (reschedule (->SimpleStately core ref)))))
