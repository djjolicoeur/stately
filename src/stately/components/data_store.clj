(ns stately.components.data-store)


(defprotocol DataStore
  (get-data [this] "Retrieve data store"))

(defrecord AtomDataStore [data-atom]
  DataStore
  (get-data [this] data-atom))
