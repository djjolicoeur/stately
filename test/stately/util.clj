(ns stately.util
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [stately.components.executor :as exec]
            [stately.components.data-store :as data-store]
            [stately.components.state-store :as  state-store]
            [stately.core :as stately]
            [stately.graph.node :as node]
            [stately.graph.directed-graph :as dag]
            [stately.machine.state-machine :as sm])
  (:import java.util.concurrent.Executors))


(def test-schema
  [{:db/id #db/id[:db.part/db]
    :db/ident :model/type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :applicant/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :applicant/school
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :applicant/status
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :applicant/gpa
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :application.state/ref
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :application.state/state
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :application.state/next-in
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :application.state/accept?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :application.state/reject?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :application.state/tx
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}])


(def fresh-facts
  [{:db/id #db/id[:db.part/user]
    :model/type :applicant
    :applicant/name "Dan"
    :applicant/school "UMBC"
    :applicant/gpa 3.3}

   {:db/id #db/id[:db.part/user]
    :model/type :applicant
    :applicant/name "Casey"
    :applicant/school "Salisbury"
    :applicant/gpa 3.5}

   {:db/id #db/id[:db.part/user]
    :model/type :applicant
    :applicant/name "Ginny"
    :applicant/school "UMD"
    :applicant/gpa 2.2}])


(def restart-facts
  [{:db/id #db/id[:db.part/user -1]
    :model/type :applicant
    :applicant/status :received
    :applicant/name "Dan"
    :applicant/school "UMBC"
    :applicant/gpa 3.3}

   {:db/id #db/id[:db.part/user -2]
    :model/type :applicant
    :applicant/status :received
    :applicant/name "Casey"
    :applicant/school "Salisbury"
    :applicant/gpa 3.5}

   {:db/id #db/id[:db.part/user -3]
    :model/type :applicant
    :applicant/name "Ginny"
    :applicant/school "UMD"
    :applicant/gpa 2.2}

   {:db/id #db/id[:db.part/user -5]
    :model/type :application.state
    :application.state/ref #db/id[:db.part/user -1]
    :application.state/state :receive-application
    :application.state/accept? false
    :application.state/reject? false
    :application.state/next-in 3000
    :application.state/tx (java.util.Date.)}

   {:db/id #db/id[:db.part/user -6]
    :model/type :application.state
    :application.state/ref #db/id[:db.part/user -2]
    :application.state/state :receive-application
    :application.state/accept? false
    :application.state/reject? false
    :application.state/next-in 3000
    :application.state/tx (java.util.Date.)}])


(defn bootstrap [conn]
  (doseq [attr test-schema]
      @(d/transact conn [attr])))

(defn load-facts [conn facts]
  @(d/transact conn facts))

(defrecord Datomic [datomic-uri facts conn]
  component/Lifecycle
  (start [this]
    (println "Starting DB " nil)
    (if conn
      this
      (let [datomic-uri (str datomic-uri "-" (java.util.UUID/randomUUID))
            db (d/create-database datomic-uri)
            conn (d/connect datomic-uri)]
        (println "Loading Schema")
        (bootstrap conn)
        (println "Loading Facts")
        (load-facts conn facts)
        (assoc this :conn conn :datomic-uri datomic-uri))))
  (stop [this]
    (if conn
      (do
        (println "Stopping DB")
        (println "Deleting DB")
        (d/delete-database (:datomic-uri this))
        (println "Stopped DB")
        (assoc this :conn nil :datomic-uri nil))
      this)))

(defn datomic-db []
  (map->Datomic {}))


(defn fresh-system []
  (component/system-map
   :executor (Executors/newScheduledThreadPool 2)
   :datomic-uri "datomic:mem://stately-test"
   :facts fresh-facts
   :db (component/using (datomic-db) [:datomic-uri :facts])))


(defn restart-system []
  (component/system-map
   :executor (Executors/newScheduledThreadPool 2)
   :datomic-uri "datomic:mem://stately-test"
   :facts restart-facts
   :db (component/using (datomic-db) [:datomic-uri :facts])))


(def ^:dynamic *system*)
(def ^:dynamic *dan*)
(def ^:dynamic *casey*)
(def ^:dynamic *ginny*)

(defn by-username [db name]
  (-> (d/q '[:find ?e
             :in $ ?name
             :where [?e :applicant/name ?name]]
           db name)
      ffirst))

(defn new-fresh-app []
  (let [sys (component/start (fresh-system))]
    (alter-var-root #'*system* (constantly sys))
    (alter-var-root #'*out* (constantly *out*))
    (let [db (d/db (:conn (:db sys)))
          dan (by-username db "Dan")
          casey (by-username db "Casey")
          ginny (by-username db "Ginny")]
      (alter-var-root #'*dan* (constantly dan))
      (alter-var-root #'*casey* (constantly casey))
      (alter-var-root #'*ginny* (constantly ginny)))))

(defn new-restart-app []
  (let [sys (component/start (restart-system))]
    (alter-var-root #'*system* (constantly sys))
    (alter-var-root #'*out* (constantly *out*))
    (let [db (d/db (:conn (:db sys)))
          dan (by-username db "Dan")
          casey (by-username db "Casey")
          ginny (by-username db "Ginny")]
      (alter-var-root #'*dan* (constantly dan))
      (alter-var-root #'*casey* (constantly casey))
      (alter-var-root #'*ginny* (constantly ginny)))))

(defn stop-app []
  (component/stop *system*)
  (alter-var-root #'*system* (constantly nil))
  (alter-var-root #'*dan* (constantly nil))
  (alter-var-root #'*casey* (constantly nil))
  (alter-var-root #'*ginny* (constantly nil)))


(defn mk-executor [system]
  (exec/->BasicJavaExecutor (:executor system)))

(defn state->entity [state]
  (into {} (map (fn  [[k v]] {(->> k
                                   name
                                   (str "application.state/")
                                   keyword) v})
                state)))

(defn entity->state [entity]
  (into {} (map (fn [[k v]] {(keyword (name k)) v})
                (dissoc entity :application.state/ref :db/id :model/type))))



(defn find-ref-state [db ref]
  (-> (d/q '[:find (pull ?e [* {:application.state/ref [:db/id]}])
             :in $ ?ref
             :where [?e :application.state/ref ?ref]]
           db ref)
      ffirst))


(defn get-states [db]
  (->> (d/q '[:find (pull ?e [{:application.state/ref [:db/id]}])
              :where [?e :model/type :application.state]] db)
       (map #(get-in  (first %) [:application.state/ref :db/id]))))

(defrecord ApplicationStateStore [system]
  state-store/StateStore
  (get-state [this ref]
    (let [db (d/db (:conn (:db system)))
          state (entity->state (find-ref-state db ref))]
      state))
  (persist-state! [this ref state]
    (let [conn (:conn (:db system))
          db (d/db conn)
          eid (:db/id (find-ref-state db ref))
          state (assoc state :ref ref)
          e (assoc (state->entity state)
                   :model/type :application.state
                   :db/id (or eid (d/tempid :db.part/user)))]
      @(d/transact conn [e])))
  (evict-state! [this ref]
    (let [db (d/db (:conn (:db system)))
          state (find-ref-state db ref)]
      (when state
        @(d/transact (:conn (:db system))
                     [[:db.fn/retractEntity (:db/id state)]]))))
  (all-refs [this]
    (let [db (d/db (:conn (:db system)))]
      (get-states db))))


(defrecord DatomicDataStore [system]
  data-store/DataStore
  (get-data [this] (:conn (:db system))))

(defn data-fn [data-store ref]
  (d/pull (d/db data-store) '[*] ref))


;; state transition handlers -- given a state, what side effects should happen
;; e.g. write to the database, log, or notify the user
(defmulti handle-state-fn
  (fn [_ new-state _ _] new-state))


;; We have received an application and our stately machine
;; is now in state :receive-application. For the purposes of this
;; example, I assume we already have some record of them.
;; now we want to notify (here, just print to screen)
;; the applicant that we got their application
;; and update our record of them to reflect the same
(defmethod handle-state-fn :receive-application
  [component new-state ref data]
  (println "NOTIFICATION "
           (str "Thanks for applying, " (:applicant/name data)
                " from "  (:school data)
                ". Your application is under review."))
  @(d/transact  (data-store/get-data (stately/data-store component))
                          [[:db/add ref :applicant/status :received]]))


;; The stately machine has reached the :accept-applicant
;; state, and we which to notify the user and persist
;; that information to our data record.
(defmethod handle-state-fn :accept-applicant
  [component new-state ref data]
  (println "NOTIFICATION "
        (str "Congratulations, " (:applicant/name data) "! You "
             "have been accepted!!"))
  @(d/transact (data-store/get-data (stately/data-store component))
               [[:db/add ref :applicant/status :accepted]]))

;; Really the same as above, only this
;; applicant didn't cut the mustard.
(defmethod handle-state-fn :reject-applicant
  [component new-state ref data]
  (println "NOTIFICATION "
        (str "Sorry, " (:applicant/name data) ". You "
             "have not been accepted."))
  @(d/transact (data-store/get-data (stately/data-store component))
               [[:db/add ref :applicant/status :rejected]]))

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
(def wait-time 3000)

;; We'll make them wait after we receive
;; the application (3 seconds in this case)
(def receive-application
  (node/expiring-node
   #{:accept-applicant :reject-applicant :receive-application}
   :recieve-application
   (fn [data]
     (if (> (:applicant/gpa data) 3.0)
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
(defrecord Core [system]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  stately/IStatelyCore
  (state-machine [this] machine)
  (data-fn [this] data-fn)
  (handle-state-fn [this] handle-state-fn)
  (state-store [this] (->ApplicationStateStore system))
  (data-store [this] (->DatomicDataStore system))
  (executor [this] (mk-executor system)))
