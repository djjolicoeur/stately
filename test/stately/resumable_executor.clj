(ns stately.resumable-executor
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [stately.core :as stately]
            [stately.graph.node :as node]
            [stately.graph.directed-graph :as dag]
            [stately.machine.state-machine :as sm]
            [stately.util :refer :all]
            [datomic.api :as d]))

(use-fixtures :each
  (fn [f]
    (new-restart-app)
    (f)
    (stop-app)))


(defn count-status [db status]
  (-> (d/q '[:find ?e
             :in $ ?status
             :where [?e :applicant/status ?status]]
           db status)
      count))

(defn count-states [db]
  (-> (d/q '[:find ?e
             :in $
             :where [?e :model/type :application.state]]
           db)
      count))


(deftest test-resumable-executor
  (testing "states are moved into executor on bootstrap"
    (let [_ (log/info :task :enter :t (java.util.Date.))
          core (->Core *system*)
          _ (stately/bootstrap-executor core)
          r-count (count-status (d/db (:conn (:db *system*))) :received)
          c-count (count-states (d/db (:conn (:db *system*))))
          _ (Thread/sleep 5000)
          evicted-count (count-states (d/db (:conn (:db *system*))))
          accepted-count (count-status (d/db (:conn (:db *system*))) :accepted)
          _ (log/info :task ::exit :exit :t (java.util.Date.))]
      (is (= 2 r-count))
      (is (= 2 c-count))
      (is (= 0 evicted-count))
      (is (= 2 accepted-count)))))
