(ns stately.graph.directed-graph
  (:require [stately.graph.node :as node]
            [loom.graph :as graph]
            [loom.alg   :as alg]))



(defn edges
  "Make graph out edges out of successors of node.
   Currently, I am thinking through how to avoid having to
   explicitly define the successors of the nodes."
  [[k v]]
  (when (node/successors v)
    (loop [edges (conj (set (node/successors v)) :rej) ret (transient [])]
      (if-let [next (first edges)]
        (recur (rest edges) (conj! ret (vector k next)))
        (into [] (persistent! ret))))))



(defprotocol Graph
  (graph [this] "the graph abstraction underlying the state machine")
  (nodes [this] "the nodes associated with this state machine"))

(defrecord StatelyGraph [g nodes]
  Graph
  (graph [this] g)
  (nodes [this] nodes))

(defn new-graph [g nodes]
  (->StatelyGraph g nodes))

(defn make-graph
  "build a map of the loom graph representation
   and the node definitions to carry node meta-data.
   Use the loom digraph API to enforce FSM invariants e.g.
   every node has a path to an accept state."
  [node-map]
  {:pre
   [(> (count (filter node/accept? (vals node-map))) 0)
    (instance? stately.graph.node.StartNode (:start node-map))]}
  (let [nodes (assoc node-map :rej node/reject-node)
        gnodes (->> (map edges nodes)
                    (reduce concat))
        graph (apply graph/digraph gnodes)
        accept-nodes (keys (filter (fn [[k v]] (node/accept? v)) nodes))
        start (:start nodes)]
    (for [k (keys nodes)]
      (when-not (node/terminal-node? (get nodes k))
        (assert
         (some identity
               (map (partial alg/shortest-path graph k) accept-nodes)))))
    (new-graph graph nodes)))
