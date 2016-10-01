(ns stately.machine.state-machine
  (:require [loom.graph :as graph]
            [stately.graph.node :as node]
            [stately.graph.directed-graph :as dg]
            [clojure.tools.logging :as log]))


;; StateMachine protocol.
;; only implements advance currently.
;; Other methods might include reset and set-state
;; to accommodate migrations
(defprotocol IStateMachine
  (advance
    [g action state data]
    [g action state data event]
    "Advance to next valid state"))


;; Next state multimethod.  Allows us to use a
;; single function to determine the next state.
(defmulti next-state
  (fn [_ action & args] action))


(defn- transition-state
  [g state next]
  (if (graph/has-edge? (dg/graph g) state next)
    next
    (do
      (log/error :at ::transition-state
                 :error "Invalid State Transition from :state->:next"
                 :state state
                 :next next)
      :rej)))

;; Determine what the next state should be given an input event
(defmethod next-state :input
  ([g action state data event]
   (log/debug :at ::next-state :state state :data data :event event)
   (let [next (node/send-input (get (dg/nodes  g) state) data event)]
     (transition-state g state next)))
  ([g action state data]
   (log/debug :at ::next-state :state state :data data)
   (let [next (node/send-input (get (dg/nodes  g) state) data)]
     (transition-state g state next))))


;; Determine what the next state should be given a timeout
(defmethod next-state :expire [g action state data]
  (log/debug :at ::next-state :state state :data data)
  (let [next (node/send-timeout (get (dg/nodes g) state) data)]
    (log/debug :at ::next-state :stage :next :next next)
    (transition-state g state next)))

;; Next at mutlimethod -- not all Nodes implement TimeoutNode
(defmulti expire-in
  (fn [g _ state _]
    (when (node/terminal-node? (get (dg/nodes g) state)) :done)))

;; Terminal nodes don't give a next-at
(defmethod expire-in :done [_ _ _ _] nil)

;; calculate next at for non-terminal nodes
(defmethod expire-in :default [g _ state data]
  (node/expire-in (get  (dg/nodes g) state) data))


;; FSM definition.  Advancing returns a state
;; meta-map which contains the state itself, tx
;; timestamp, whether we are in an accept or reject state
;; and when to timeout the current state.
(defrecord StateMachine [graph]
  IStateMachine
  (advance [this action state data]
    (let [nodes (dg/nodes graph)
          current-state (if state state :start)
          next-state (next-state graph action current-state data)
          next-in (expire-in graph action next-state data)
          next-node (get nodes next-state)]
      {:state next-state
       :next-in next-in
       :accept? (node/accept? next-node)
       :reject? (node/reject? next-node)
       :tx (java.util.Date.)}))
  (advance [this action state data event]
    (let [nodes (dg/nodes graph)
          current-state (if state state :start)
          next-state (next-state graph action current-state data event)
          next-in (expire-in graph action next-state data)
          next-node (get nodes next-state)]
      {:state next-state
       :next-in next-in
       :accept? (node/accept? next-node)
       :reject? (node/reject? next-node)
       :tx (java.util.Date.)})))


;;Convenience fn for creating a StatelyGraph
(defn make-stately [nodes]
    (->StateMachine (dg/make-graph nodes)))
