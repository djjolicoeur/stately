(ns mock-system
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer (refresh)]
            [stately.components.executor :as exec]
            [stately.components.data-store :as data-store]
            [stately.components.state-store :as  state-store]
            [stately.graph.node :as node]
            [stately.graph.directed-graph :as dag]
            [stately.machine.state-machine :as sm]
            [stately.core :as stately])
  (:import java.util.concurrent.Executors))


;; System definition Stuff
(defrecord TestExecutor [executor-pool basic-executor]
  component/Lifecycle
  (start [this]
    (if basic-executor this
        (assoc this :basic-executor
               (exec/->BasicJavaExecutor executor-pool))))
  (stop [this]
    (if-not basic-executor this
            (do (.shutdown executor-pool)
                (assoc this :basic-executor nil)))))

(defn new-executor []
  (map->TestExecutor {}))

(defrecord SystemStateStore [store atom-store]
  component/Lifecycle
  (start [this]
    (if atom-store this
        (assoc this :atom-store (state-store/->AtomStateStore (atom store)))))
  (stop [this]
    (assoc this :atom-store nil)))

(defn new-state-store []
  (map->SystemStateStore {}))

(defrecord SystemDataStore [bootstrap-data data-store]
  component/Lifecycle
  (start [this]
    (if data-store this
        (let [ds (data-store/->AtomDataStore (atom bootstrap-data))]
          (assoc this :data-store ds))))
  (stop [this]
    (assoc this :data-store nil)))

(defn new-data-store []
  (map->SystemDataStore {}))



;; Test Data
(def dan "69d09e4a-982c-448f-9de3-079c58d44c50")
(def casey "6102987e-142d-4561-9482-9e609ede4e3b")
(def ginny "6102987e-142d-4561-9482-9e609ede4e2e")
(def bootstrap-data
  {dan
   {:firstname "Daniel"
    :school "UMBC"
    :gpa 3.3}
   casey
   {:firstname "Casey"
    :school "Salisbury"
    :gpa 3.5}
   ginny
   {:firstname "Ginny"
    :school "UMD"
    :gpa 2.2}})


;; Component that implements StatelyComponent
(defrecord BasicComponent [state-store data-store executor]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  stately/StatelyComponent
  (state-store [this] (:atom-store state-store))
  (data-store [this] (:data-store data-store))
  (executor [this] (:basic-executor executor)))

(defn stately-component []
  (map->BasicComponent {}))


;; System to bootstrap user ns examples
(defn dev-system []
  (component/system-map
   :bootstrap-data bootstrap-data
   :store {}
   :executor-pool (Executors/newScheduledThreadPool 2)
   :executor (component/using
              (new-executor) [:executor-pool])
   :state-store (component/using
                 (new-state-store) [:store])
   :data-store (component/using
                (new-data-store) [:bootstrap-data])
   :stately-component
   (component/using
    (stately-component)
    [:state-store :data-store :executor])))
