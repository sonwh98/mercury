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
  (let [topic-chan (a/chan (a/dropping-buffer 10))
        kill-chan (a/chan 1)]
    (a/sub message-publication topic topic-chan)
    (a/go
      (a/<! kill-chan)
      (unsubscribe topic-chan topic))
    [topic-chan kill-chan]))

(defn unsubscribe-to [[topic-chan kill-chan]]
  (a/put! kill-chan true)
  (a/close! topic-chan)
  (a/close! kill-chan))

(defn on
  [topic call-back-fn]
  (let [[topic-chan kill-channel :as subscription] (subscribe-to topic)]
    (a/go-loop []
      (call-back-fn (a/<! topic-chan))
      (recur))
    subscription))

(defn whenever
  "returns a closure that takes a call-back-fn which is executed when ever the topic message is broadcasted"
  [topic]

  (comment ;;example using whenever
    (def when-ready (whenever :ready))

    (def subscriptions (when-ready (fn [msg]
                                   (prn "i'm ready " msg))))
    (count d)
    (map #(unsubscribe-to %) )
    (broadcast [:ready true])
    )
  
  (let [topic-message (atom nil)
        subscription (on topic #(reset! topic-message %))]
    (fn [call-back-fn]
      (if @topic-message
        (do
          (call-back-fn @topic-message)
          [subscription])
        [subscription (on topic (fn [this-topic-message]
                                (reset! topic-message this-topic-message)
                                (call-back-fn this-topic-message)))]))))

(defn postpone [execute-fn ms]
  (a/go (a/<! (a/timeout ms))
        (execute-fn)))
