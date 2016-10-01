(ns college-application
  (:require [com.stuartsierra.component :as component]
            [stately.components.executor :as exec]
            [stately.components.data-store :as data-store]
            [stately.components.state-store :as  state-store]
            [stately.graph.node :as node]
            [stately.graph.directed-graph :as dag]
            [stately.machine.state-machine :as sm]
            [stately.core :as stately])
  (:import java.util.concurrent.Executors))

;;-----------------------------------------------------------
;; Super simple Stately IMPL. Someone submits a college application
;; and we accept or deny.  We let the Stately machine drive the
;; state of the process and handle side effects as a result of
;; moving to a state.
;;
(defn simple-data-fn [data-store ref]
  (get  @data-store ref))

;; state transition handlers -- given a state, what side effects should happen
;; e.g. write to the database, log, or notify the user
(defmulti handle-state-fn
  (fn [_ new-state _ _] new-state))


(defn update-status
  "Helper fn to update in atom"
  [old ref status]
  (assoc old ref (assoc (get old ref) :status status)))

;; We have received an application and our stately machine
;; is now in state :receive-application. For the purposes of this
;; example, I assume we already have some record of them.
;; now we want to notify (here, just print to screen)
;; the applicant that we got their application
;; and update our record of them to reflect the same
(defmethod handle-state-fn :receive-application
  [component new-state ref data]
  (println (str "Thanks for applying, " (:firstname data)
                " from "  (:school data)
                ". Your application is under review."))
  (swap! (data-store/get-data
          (stately/data-store component)) #(update-status % ref :received)))


;; The stately machine has reached the :accept-applicant
;; state, and we which to notify the user and persist
;; that information to our data record.
(defmethod handle-state-fn :accept-applicant
  [component new-state ref data]
  (println (str "Congratulations, " (:firstname data) "! You "
                "have been accepted!!"))
  (swap! (data-store/get-data
          (stately/data-store component)) #(update-status % ref :accepted)))

;; Really the same as above, only this
;; applicant didn't cut the mustard.
(defmethod handle-state-fn :reject-applicant
  [component new-state ref data]
  (println (str "Sorry, " (:firstname data) ". You "
                "have not been accepted."))
  (swap! (data-store/get-data
          (stately/data-store component)) #(update-status % ref :rejected)))

;; The handle state fn needs to return true for the machine
;; to advance.  This may not always be the desired behavior,
;; but for the purposes of this exercise, we'll just
;; advance if we have not specified.
(defmethod handle-state-fn :default [_ _ _ _] true)


;;---------------------------------------------------------------------
;; Node Definitions
;;
;; This is where we define the nodes that will drive the behavior
;; of the state machine.
;;

(def start-node (node/bootstrap-node :receive-application))

;; Here's the knob to turn if you want to examine
;; what is happening in the state store.  I'm setting
;; it to 3 seconds for quick examples
(def wait-time 30000)

;; We'll make them wait after we receive
;; the application (3 seconds in this case)
(def receive-application
  (node/expiring-node
   #{:accept-applicant :reject-applicant :receive-application}
   :recieve-application
   (fn [data]
     (if (> (:gpa data) 3.0)
       :accept-applicant
       :reject-applicant))
   (constantly wait-time)))


(def accept-applicant node/accept-node)

;; I know it seems strange that this wouldn't
;; that reject-applicant is an accept node,
;; but in the context of the machine it makes
;; sense
(def reject-applicant node/accept-node)

(def college-application-nodes
  {:start start-node
   :receive-application receive-application
   :accept-applicant accept-applicant
   :reject-applicant reject-applicant})


;; Define the state machine that drives the behaviour
(def machine (sm/make-stately college-application-nodes))


;;core

;; Component that implements IStatelyCore
(defrecord Core [state-store data-store executor]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  stately/IStatelyCore
  (state-machine [this] machine)
  (data-fn [this] simple-data-fn)
  (handle-state-fn [this] handle-state-fn)
  (state-store [this] (:atom-store state-store))
  (data-store [this] (:data-store data-store))
  (min-scheduler-interval [this] 0)
  (reschedule-delta-max [this] Integer/MAX_VALUE)
  (executor [this] (:basic-executor executor)))

(defn new-core []
  (map->Core {}))
