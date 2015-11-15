(ns stately.core
  (:require [stately.machine.state-machine :as sm]
            [stately.graph.directed-graph :as dg]
            [stately.graph.node :as node]
            [stately.components.data-store :as ds]
            [stately.components.state-store :as ss]
            [stately.components.executor :as exec]
            [clojure.tools.logging :refer [info]])
  (:import java.util.concurrent.Executors
           java.util.concurrent.TimeUnit))

;;Defines the basic protocols necessary to run the stately machine
(defprotocol StatelyComponent
  (state-store [this] "Something that implements the state store protocols")
  (data-store [this] "Something that implements the data store protocols")
  (executor [this] "Something that implements the Executor protocols"))



;; Protocol that defines everything needed to build a
;; Stately component
(defprotocol StatelyProtocol
  (data [this] [this event] "get data to deliver from app state/DB")
  (input [this event] "Send input to State Machine")
  (expire [this] "expire current state")
  (handle-state [this new-state] "handle side effects of new state")
  (get-state [this] "look up current state")
  (persist-state [this new-state] "save state of workflow")
  (schedule-executor [this new-state] "schedule timeout"))


(declare executable)

;; On of the simplest implementations I can think of to illustrate
;; the major points of what I'm trying to do with this project.
;; Here we have
;; - state-machine - the definition of a state machine
;; - component - a StatelyComponent
;; - id - a unique identifier for a particular instance of state
;; - ref - reference into the data-store
(defrecord SimpleStately
    [state-machine component ref data-fn state-handler-fn]
  StatelyProtocol
  (get-state [this] (ss/get-state (state-store component) ref))
  (persist-state [this new-state]
    (info :new-state new-state)
    (if (:accept? new-state)
      (ss/evict-state! (state-store component) ref)
      (ss/persist-state! (state-store component) ref new-state)))
  (data [this]
    (data-fn (ds/get-data (data-store component)) ref))
  (data [this event]
    (data-fn (ds/get-data (data-store component)) ref))
  (handle-state [this new-state]
    (state-handler-fn component (:state new-state) ref (data this)))
  (schedule-executor [this new-state]
    (exec/schedule (executor component)
                   (executable state-machine component ref
                               data-fn state-handler-fn new-state)
                   (:next-in new-state)))
  (input [this event]
    (info "RECEIVED EVENT" event)
    (let [state (get-state this)
          data (data this event)
          new-state (sm/advance state-machine :input (:state state) data event)]
      (info "TRANSITIONED TO STATE" new-state)
      (when (handle-state this new-state)
        (persist-state this new-state)
        (when (:next-in new-state)
          (schedule-executor this new-state)))))
  (expire [this]
    (info "EXPIRING" ref)
    (let [state (get-state this)
          data (data this)
          new-state (sm/advance state-machine :expire (:state state) data)]
      (info "EXPIRED TO STATE " new-state)
      (when (handle-state this new-state)
        (persist-state this new-state)
        (when (:next-in new-state)
          (schedule-executor this new-state))))))

(defn executable [sm component ref data-fn state-handler-fn state]
  (fn []
    (info "EXECUTING SCHEDULED JOB" "")
    (let [current (->SimpleStately sm component ref data-fn state-handler-fn)
          new-state (get-state current)]
      (info "COMPARING NEW STATE" new-state "OLD STATE" state)
      (when (= state new-state)
        (expire current)))))

(defn defstately* [sm data-fn state-handler-fn]
  (fn [component ref]
    (->SimpleStately sm component ref data-fn state-handler-fn)))

(defmacro defstately [name state-machine data-fn state-handler-fn]
  `(def ~name (defstately* ~state-machine ~data-fn ~state-handler-fn)))
