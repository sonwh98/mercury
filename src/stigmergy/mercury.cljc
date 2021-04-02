(ns stigmergy.mercury
  (:require [clojure.core.async :as a :include-macros true]))

;;a message is a vector of the form [topic value]
;;the topic can be any value but should be a keyword
(defonce message-bus (a/chan 10))
(defonce message-publication (a/pub message-bus (fn [msg]
                                                  (if (vector? msg)
                                                    (first msg)
                                                    :default))))
(defn broadcast [msg]
  (a/put! message-bus msg))

(defn unsubscribe
  [channel topic]
  (a/unsub message-publication topic channel))

(defn subscribe-to
  [topic]
  (let [channel (a/chan (a/dropping-buffer 10))
        kill-channel (a/chan 1)]
    (a/sub message-publication topic channel)
    (a/go
      (a/<! kill-channel)
      (unsubscribe channel topic))
    [channel kill-channel]))

(defn on
  [topic call-back-fn]
  (let [[topic-chan kill-channel :as channels] (subscribe-to topic)]
    (a/go-loop []
      (call-back-fn (a/<! topic-chan))
      (recur))
    channels))

(defn whenever
  "returns a closure that takes a call-back-fn which is executed when ever the topic message been broadcasted"
  [topic]
  (let [topic-message (atom nil)]
    (on topic #(reset! topic-message %))
    (fn [call-back-fn]
      (if @topic-message
        (call-back-fn @topic-message)
        (on topic (fn [this-topic-message]
                    (reset! topic-message this-topic-message)
                    (call-back-fn this-topic-message)))))))

(defn postpone [execute-fn ms]
  (a/go (a/<! (a/timeout ms))
        (execute-fn)))
