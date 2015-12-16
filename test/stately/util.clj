(ns stately.util
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :refer [debug info warn error]]
            [stately.components.executor :as exec]
            [stately.components.data-store :as data-store]
            [stately.components.state-store :as  state-store])
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
  [{:db.id #db/id[:db.part/user]
    :model/type :applicant
    :applicant/name "Dan"
    :applicant/school "UMBC"
    :applicant/gpa 3.3}

   {:db.id #db/id[:db.part/user]
    :model/type :applicant
    :applicant/name "Casey"
    :applicant/school "Salisbury"
    :applicant/gpa 3.5}

   {:db.id #db/id[:db.part/user]
    :model/type :applicant
    :applicant/name "Ginny"
    :applicant/school "UMD"
    :applicant/gpa 2.2}])


(def restart-facts
  [{:db.id #db/id[:db.part/user -1]
    :model/type :applicant
    :applicant/name "Dan"
    :applicant/school "UMBC"
    :applicant/gpa 3.3}

   {:db.id #db/id[:db.part/user -2]
    :model/type :applicant
    :applicant/name "Casey"
    :applicant/school "Salisbury"
    :applicant/gpa 3.5}

   {:db.id #db/id[:db.part/user -3]
    :model/type :applicant
    :applicant/name "Ginny"
    :applicant/school "UMD"
    :applicant/gpa 2.2}

   {:db.id #db/id[:db.part/user]
    :model/type :application.state
    :application.state/ref -1
    :application.state/state :recieve-application
    :application.state/accept? false
    :application.state/reject? false
    :application.state/tx (java.util.Date.)}

   {:db.id #db/id[:db.part/user]
    :model/type :application.state
    :application.state/ref -2
    :application.state/state :recieve-application
    :application.state/accept? false
    :application.state/reject? false
    :application.state/tx (java.util.Date.)}])


(defn bootstrap [conn]
  (doseq [attr test-schema]
      @(d/transact conn [attr])))

(defn load-facts [conn facts]
  @(d/transact conn facts))

(defrecord Datomic [datomic-uri facts conn]
  component/Lifecycle
  (start [this]
    (info "START DB " nil)
    (if conn
      this
      (let [rand (java.util.UUID/randomUUID)
            datomic-uri (str datomic-uri "-" (java.util.UUID/randomUUID))
            _ (info "URI " datomic-uri)
            db (d/create-database datomic-uri)
            conn (d/connect datomic-uri)]
        (info "LOADING SCHEMA" nil)
        (bootstrap conn)
        (info "LOADING FACTS" nil)
        (load-facts conn facts)
        (assoc this :conn conn :datomic-uri datomic-uri))))
  (stop [this]
    (if conn
      (do
        (info "STOPPING DB" nil)
        (info "DELETING DB" nil)
        (d/delete-database (:datomic-uri this))
        (info "STOPPED DB" nil)
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

(defn new-fresh-app []
  (let [sys (component/start (fresh-system))]
    (alter-var-root #'*system* (constantly sys))))

(defn new-restart-app []
  (let [sys (component/start (restart-system))]
    (alter-var-root #'*system* (constantly sys))))

(defn stop-app []
  (component/stop *system*)
  (alter-var-root #'*system* (constantly nil)))


(defn mk-executor [system]
  (exec/->BasicJavaExecutor (:executor system)))

(defn state->entity [state]
  (into {} (map (fn  [[k v]] {(->> k
                                   name
                                   (str "application.state/")
                                   keyword) v})
                state)))

(defn entity->state [entity]
  (into {} (map (fn [[k v]] {(name k) v})
                (dissoc entity :application.state/ref))))

(defn find-ref-state [db ref]
  (-> (d/q '[:find (pull ?e [* {:application.state/ref [:db/id]}])
             :in $ ?ref
             :where [?e :application.state/ref ?ref]]
           db ref)
      ffirst))

(defn get-states [db]
  (->> (d/q '[:find (pull ?e [:application.state/ref])
              :where [?e :model/type :application.state]]))
  (map :application.state/ref))

(defrecord DatomicStateStore [system]
  state-store/StateStore
  (get-state [this ref]
    (let [db (d/db (:conn (:db system)))]
      (entity->state (find-ref-state db ref))))
  (persist-state! [this ref state]
    (let [conn (:conn (:db system))
          state (assoc state :ref ref)
          e (state->entity state)]
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
