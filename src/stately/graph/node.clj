(ns stately.graph.node)


;;------------------------------------------------------------------------------
;; Node protocol definitions.  Nodes are the building blocks of our state
;; machines.  What follows is the minimum covering set of what I think
;; is necessary to abstract framework on defining nodes that are capable
;; of representing a state machine that also has a notion of time.
;;


(defprotocol Node
  "Basic protocols necessary to constitute a node"
  (successors [this] "Return set of successor names")
  (accept? [this] "is this an accept state? (terminal?)")
  (reject? [this] "is this a reject stat? (terminal...?)"))


(defprotocol EventNode
  "Protocol for nodes that will receive events"
  (send-input [this data] [this data event] "Deliver input"))

(defprotocol TimeoutNode
  "protocols for nodes that will expire, that is are bound by time"
  (expire-in [this data] "Return a wait time (in milliseconds) based on data")
  (send-timeout [this data] "Deliver timeout"))


;;------------------------------------------------------------------------------
;; Node Implementations. most nodes will take a decision fn.  this is
;; this function, given the data and possibly an event, should decide the
;; next step to take.   We are "sending" information, e.g. an event
;; or a timeout to a node, and the node should decide what to do given that
;; information.


;; Node that bootstraps the state machine
;; requires a list of possible successor nodes
;; and the function that sends data to the machine
(defrecord StartNode [successors decision-fn]
  Node
  (successors [this] successors)
  (accept? [this] false)
  (reject? [this] false)
  EventNode
  (send-input [this data]
    (decision-fn data))
  (send-input [this data event]
    (decision-fn data event)))


;; basic nodes = node + event node + timeout node
;; as defined by the functions passed in as args
;; decision-fn -  where do I go given an event or some data?
;; expire-fn - where do I go when I timeout?
;; expire-in-fn - how long do I wait?
(defrecord BasicNode [successors decision-fn expire-fn expire-in-fn]
  Node
  (successors [this] successors)
  (accept? [this] false)
  (reject? [this] false)
  EventNode
  (send-input [this data]
    (decision-fn data))
  (send-input [this data event]
    (decision-fn data event))
  TimeoutNode
  (send-timeout [this data]
    (expire-fn data))
  (expire-in [this data]
    (expire-in-fn data)))


;; Accept nodes they signal that our machine has reached
;; the accept state. Pretty boring.
(defrecord AcceptNode []
  Node
  (successors [this] nil)
  (accept? [this] true)
  (reject? [this] false))


;; Reject nodes signal that our machine has reached a reject
;; or failure state.  Maybe not so boring, but doesn't do much
;; for us either
(defrecord RejectNode []
  Node
  (successors [this] nil)
  (accept? [this] false)
  (reject? [this] true))


;;------------------------------------------------------------------------------
;; Node helpers.  In the development of applications that have had needs that
;; facilitate the need of mechanisms such as this, I have noticed certain
;; types of these nodes are extremely useful and as such I have developed
;; a collection of helpers to aid in the ease of use.  This is by no means
;; exhaustive and they should not restrict the use of creating custom nodes

(def accept-node (->AcceptNode))
(def reject-node (->RejectNode))



(defn bootstrap-node
  "Bootstraps the State Machine."
  [node-name]
  (->StartNode #{node-name} (constantly node-name)))



(defn decision-node
  "Decision node.
   decision-fn definition MUST handle [data] AND [data event] arg vectors
   decision nodes are the 'routers' of our state machines."
  [successors decision-fn]
  (->BasicNode successors decision-fn decision-fn (constantly 0)))


(defn wait-node
  "Wait node.
  Sometimes we just need to wait indefinitely for some kind
  of input. This node will do exactly that.  It waits for either
  a time given by a fn passed in or indefinitely for input, should
  you so desire"
  ([successors decision-fn expire-node expire-in-fn]
   (->BasicNode successors
                decision-fn
                (constantly expire-node)
                expire-in-fn))
  ([successors decision-fn expire-node]
   (wait-node successors
                       decision-fn
                       expire-node
                       (constantly Integer/MAX_VALUE))))


(defn action-node
  "Action Node
   State Machine will immediately execute handle-state fn and move
   to next node, unless input interrupts.  Interrupt node
   can be self referencing as well (pipe node back to this node)"
  ([successors interrupt-node next-node]
    (->BasicNode successors
              (constantly interrupt-node)
              (constantly next-node)
              (constantly 0)))
  ([successors next-node]
   (action-node successors next-node next-node))
  ([next-node]
   (action-node #{next-node} next-node)))



(defn expiring-node
  "Expiring Node
  Will wait for the time designated by expire-in-fn
  and execute timeout-fn.  any input into the machine before expire
  will go to the interrupt-node"
  [successors interrupt-node expire-fn expire-in-fn]
  (->BasicNode successors
            (constantly interrupt-node)
            expire-fn
            expire-in-fn))



(defn terminal-node? [node]
  "Am I done?"
  (or (accept? node) (reject? node)))
