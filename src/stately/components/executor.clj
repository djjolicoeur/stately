(ns stately.components.executor
  (:import java.util.concurrent.Executors
           java.util.concurrent.TimeUnit))

(defprotocol Executor
  (schedule [this runnable time] "schedule a task"))


(defrecord BasicJavaExecutor [executor]
  Executor
  (schedule [this runnable time]
    (.schedule executor runnable time
                             java.util.concurrent.TimeUnit/MILLISECONDS)))
