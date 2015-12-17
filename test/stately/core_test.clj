(ns stately.core-test
  (:require [clojure.test :refer :all]
            [stately.core :as stately]
            [stately.graph.node :as node]
            [stately.graph.directed-graph :as dag]
            [stately.machine.state-machine :as sm]
            [stately.util :refer :all]
            [datomic.api :as d]))


(use-fixtures :each
  (fn [f]
    (new-fresh-app)
    (f)
    (stop-app)))

(defn run-dans-application
  "Dan should get accepted"
  [core]
  (let [appl (stately/stately core *dan*)]
    (stately/input appl {})))

(defn run-caseys-application
  "Casey should get accepted, too....she's smarter than me"
  [core]
  (let [appl (stately/stately core *casey*)]
    (stately/input appl {})))

(defn run-ginnys-application
  "Ginny won't get accepted.  that's ok, though.  Ginnys my dog."
  [core]
  (let [appl (stately/stately core *ginny*)]
    (stately/input appl {})))

(defn run-everyone [core]
  (run-dans-application core)
  (run-caseys-application core)
  (run-ginnys-application core)
  (println "STATUS "
        (str "You should see results in ~" (/  wait-time 1000)
             " seconds")))

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

;;Tests
(deftest stately-test
  (testing "simple stately tests"
    (let [core (->Core *system*)
          _ (run-everyone core)
          _ (Thread/sleep 1000)
          r-count (count-status (d/db (:conn (:db *system*))) :received)
          c-count (count-states (d/db (:conn (:db *system*))))
          _ (Thread/sleep 4000)
          evicted-count (count-states (d/db (:conn (:db *system*))))
          accepted-count (count-status (d/db (:conn (:db *system*))) :accepted)
          rejected-count (count-status (d/db (:conn (:db *system*))) :rejected)]
      (is (= 3 r-count))
      (is (= 3 c-count))
      (is (= 0 evicted-count))
      (is (= 2 accepted-count))
      (is (= 1 rejected-count)))))
