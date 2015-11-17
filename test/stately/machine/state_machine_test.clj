(ns stately.machine.state-machine-test
  (:require [clojure.test :refer :all]
            [stately.graph.node :as node]
            [stately.machine.state-machine :refer :all]))



(deftest test-state-machine
  (testing "test state machine behaviour"
    (let [n1 (node/bootstrap-node :foo)
          n2 (node/->Node #{:foo :bar}
                          (constantly :foo)
                          (constantly :bar)
                          (constantly 0))
          test-nodes {:start n1
                      :foo n2
                      :bar node/accept-node}
          machine (make-stately test-nodes)
          advance-start-basic (advance machine :input nil {})
          advance-start-event (advance machine :input nil {} {})
          state-from-foo-basic (advance machine :input :foo {})
          state-from-foo-event (advance machine :input :foo {} {})
          expire-from-foo (advance machine :expire :foo {})]
      (is (= :foo (:state advance-start-basic)))
      (is (= false (:accept? advance-start-basic)))
      (is (= false (:reject? advance-start-basic)))
      (is (= 0 (:next-in advance-start-basic)))
      (is (= :foo (:state advance-start-event)))
      (is (= false (:accept? advance-start-event)))
      (is (= false (:reject? advance-start-event)))
      (is (= :foo (:state state-from-foo-basic)))
      (is (= false (:accept? state-from-foo-basic)))
      (is (= false (:reject? state-from-foo-basic)))
      (is (= :foo (:state state-from-foo-event)))
      (is (= false (:accept? state-from-foo-event)))
      (is (= false (:reject? state-from-foo-event)))
      (is (= :bar (:state expire-from-foo)))
      (is (= true (:accept? expire-from-foo)))
      (is (= false (:reject? expire-from-foo))))))
