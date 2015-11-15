(ns stately.components.store)

(defprotocol Store
  (get-state [this id] "Retrieve state store"))
