(ns stately.util
  (:require [datomic.api :as d]))


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


(def fresh-test-facts
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
  (let [schema (io/reader "resources/schema.edn")]
    (doseq [datom (datomic.Util/readAll schema)]
      @(d/transact conn [datom]))))

(defn load-facts [conn]
  (let [facts (io/reader "resources/dev-facts.edn")]
    (doseq [datom (datomic.Util/readAll facts)]
      @(d/transact conn [datom]))))

(defrecord Datomic [datomic-uri conn]
  component/Lifecycle
  (start [this]
    (info "START DB " nil)
    (if conn
      this
      (let [_ (info "URI " datomic-uri)
            datomic-uri (cond-> datomic-uri
                          dev-facts? (str "-" (java.util.UUID/randomUUID)))
            db (d/create-database datomic-uri)
            conn (d/connect datomic-uri)]
        (info "LOADING SCHEMA" nil)
        (bootstrap conn)
        (when dev-facts?
          (info "LOADING FACTS" nil)
          (load-facts conn))
        (assoc this :conn conn :datomic-uri datomic-uri))))
  (stop [this]
    (if conn
      (do
        (info "STOPPING DB" nil)
        (when dev-facts?
          (info "DELETING DB" nil)
          (d/delete-database (:datomic-uri this)))
        (info "STOPPED DB" nil)
        (assoc this :conn nil :datomic-uri nil))
      this)))

(defn new-datomic-db []
  (map->Datomic {}))
