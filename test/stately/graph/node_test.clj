(ns stately.graph.node-test
  (:require [clojure.test :refer :all]
            [stately.graph.node :refer :all]))



;; Test basic node definitions


(deftest test-start-node
  (testing "start node functionality"
    (let [succ-list #{:foo :bar :baz}
          decision (fn [data]
                     (condp = data
                       :foo :bar
                       :bar :baz
                       :baz :foo))
          node (->StartNode succ-list decision)]
      (is (= succ-list (successors node)))
      (is (= false (accept? node)))
      (is (= false (reject? node)))
      (is (= :bar (send-input node :foo)))
      (is (= :baz (send-input node :bar)))
      (is (= :foo (send-input node :baz))))))


(deftest test-node
  (testing "node functionality"
    (let [succ-list #{:foo :bar :baz}
          decision  #(condp = %1
                       :foo :bar
                       :bar :baz
                       :baz :foo)
          ex-in (fn [data]
                  (condp = data
                    :foo 0
                    :bar 1
                    :baz 2))
          node (->Node succ-list decision decision ex-in)]
      (is (= succ-list (successors node)))
      (is (= false (accept? node)))
      (is (= false (reject? node)))
      (is (= :bar (send-input node :foo)))
      (is (= :baz (send-input node :bar)))
      (is (= :foo (send-input node :baz)))
      (is (= :bar (send-timeout node :foo)))
      (is (= :baz (send-timeout node :bar)))
      (is (= :foo (send-timeout node :baz)))
      (is (= 0 (expire-in node :foo)))
      (is (= 1 (expire-in node :bar)))
      (is (= 2 (expire-in node :baz))))))


(deftest test-accept-node
  (testing "testing accept node"
    (let [node (->AcceptNode)]
      (is (= nil (successors node)))
      (is (= true (accept? node)))
      (is (= false (reject? node))))))


(deftest test-accept-node
  (testing "testing reject node"
    (let [node (->RejectNode)]
      (is (= nil (successors node)))
      (is (= false (accept? node)))
      (is (= true (reject? node))))))


;; Test node helpers


(deftest test-bootstrap-node
  (testing "test bootstrap node helper"
    (let [node (bootstrap-node :foo)]
      (is (= #{:foo} (successors node)))
      (is (= :foo (send-input node {})))
      (is (= false (accept? node)))
      (is (= false (reject? node))))))


(deftest test-decision-node
  (testing "test decision node helper"
    (let [succ-list #{:foo :bar :baz}
          dec #(condp = %1
                 :foo :bar
                 :bar :baz
                 :baz :foo)
          node (decision-node succ-list dec)]
      (is (= succ-list (successors node)))
      (is (= false (accept? node)))
      (is (= false (reject? node)))
      (is (= :bar (send-input node :foo)))
      (is (= :baz (send-input node :bar)))
      (is (= :foo (send-input node :baz)))
      (is (= :bar (send-timeout node :foo)))
      (is (= :baz (send-timeout node :bar)))
      (is (= :foo (send-timeout node :baz)))
      (is (= 0 (expire-in node :foo)))
      (is (= 0 (expire-in node :bar)))
      (is (= 0 (expire-in node :baz))))))


(deftest test-wait-node
  (testing "test wait node helping"
    (let [succ-list #{:foo :bar :baz}
          dec #(condp = %1
                 :foo :bar
                 :bar :baz
                 :baz :foo)
          node (wait-node succ-list dec :baz)]
      (is (= succ-list (successors node)))
      (is (= false (accept? node)))
      (is (= false (reject? node)))
      (is (= :bar (send-input node :foo)))
      (is (= :baz (send-input node :bar)))
      (is (= :foo (send-input node :baz)))
      (is (= :baz (send-timeout node :foo)))
      (is (= :baz (send-timeout node :bar)))
      (is (= :baz (send-timeout node :baz)))
      (is (= Integer/MAX_VALUE (expire-in node :foo)))
      (is (= Integer/MAX_VALUE (expire-in node :bar)))
      (is (= Integer/MAX_VALUE (expire-in node :baz))))))


(deftest test-action-node
  (testing "testing action node"
    (let [succ-list #{:bar}
          node (action-node :bar)]
      (is (= succ-list (successors node)))
      (is (= false (accept? node)))
      (is (= false (reject? node)))
      (is (= :bar (send-input node :foo)))
      (is (= :bar (send-input node :bar)))
      (is (= :bar (send-input node :baz)))
      (is (= :bar (send-timeout node :foo)))
      (is (= :bar (send-timeout node :bar)))
      (is (= :bar (send-timeout node :baz)))
      (is (= 0 (expire-in node :foo)))
      (is (= 0 (expire-in node :bar)))
      (is (= 0 (expire-in node :baz))))))


(deftest test-expiring-node
  (testing "test expiring node helping"
    (let [succ-list #{:foo :bar :baz}
          dec #(condp = %1
                 :foo :bar
                 :bar :baz
                 :baz :foo)
          ex-in (constantly 100)
          interrupt :bar
          node (expiring-node succ-list :bar dec ex-in)]
      (is (= succ-list (successors node)))
      (is (= false (accept? node)))
      (is (= false (reject? node)))
      (is (= :bar (send-input node :foo)))
      (is (= :bar (send-input node :bar)))
      (is (= :bar (send-input node :baz)))
      (is (= :bar (send-timeout node :foo)))
      (is (= :baz (send-timeout node :bar)))
      (is (= :foo (send-timeout node :baz)))
      (is (= 100 (expire-in node :foo)))
      (is (= 100 (expire-in node :bar)))
      (is (= 100 (expire-in node :baz))))))
