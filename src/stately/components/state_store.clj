(ns stately.components.state-store)

(defprotocol StateStore
  (get-state [this ref] "Retrieve state store")
  (persist-state! [this ref state] "save the state to the store")
  (evict-state! [this ref] "evict state")
  (all-refs [this]
    "list of refs derived from persisted states (for rebuilding executor)"))

(defrecord AtomStateStore [state-atom]
  StateStore
  (get-state [this ref]
    (get @state-atom ref))
  (persist-state! [this ref state]
    (swap! state-atom assoc ref state))
  (evict-state! [this ref]
    (swap! state-atom dissoc ref))
  (all-states [this] (keys @state-map)))
