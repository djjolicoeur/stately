(ns stately.graph.directed-graph-test
  (:require [clojure.test :refer :all]
            [stately.graph.node :as node]
            [stately.graph.directed-graph :refer :all]))

(deftest test-edges
  (testing "edges makes edges"
    (let [n1 (node/bootstrap-node :second-node)
          edges1 (set (edges (first {:start n1})))
          n2 (node/->Node #{:foo :bar :baz}
                          (constantly :foo)
                          (constantly :bar)
                          (constantly 0))
          edges2 (set (edges (first {:foo n2})))]
      (is (= #{[:start :rej] [:start :second-node]} edges1))
      (is (= #{[:foo :baz] [:foo :rej] [:foo :bar] [:foo :foo]} edges2)))))


(deftest test-make-graph
  (testing "make graph tests"
    (let [n1 (node/bootstrap-node :foo)
          n2 (node/->Node #{:foo :bar :baz}
                          (constantly :foo)
                          (constantly :bar)
                          (constantly 0))
          test-nodes {:start n1
                 :foo n2
                 :bar node/accept-node}
          created (make-graph test-nodes)
          created-graph (graph created)
          created-nodes (nodes created)
          expected-nodes (assoc test-nodes :rej node/reject-node)]
      (is (= #{:baz :rej :bar :start :foo} (:nodeset created-graph)))
      (is (= {:start #{:rej :foo}
              :foo #{:baz :rej :bar :foo}} (:adj created-graph)))
      (is (= {:rej #{:start :foo}
              :foo #{:start :foo}
              :baz #{:foo}
              :bar #{:foo}} (:in created-graph)))
      (is (= expected-nodes created-nodes)))))
