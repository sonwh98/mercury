(ns stigmergy.mercury
  (:require [clojure.core.async :as a :include-macros true]))

;;a message is a vector of the form [topic value]
;;the topic can be any value but should be a keyword
(defonce message-bus (a/chan 1024))
(defonce message-publication (a/pub message-bus (fn [msg]
                                                  (if (vector? msg)
                                                    (first msg)
                                                    :default))))
(defn broadcast
  "one to many broadcast to all listener of the msg. msg is a vector [topic msg-val] where topic is any value used
  to tag the msg-val."
  [msg]
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
  "behaves like a javascript `on` event listener. takes a keyword topic and a callback fn. Whenever
  the topic message is broadcasted, the callback fn is called. a subscription is returned that
  can be passed to unsubscribe-to function to unregister the callback from recieving events"
  [topic call-back-fn]
  (comment 
    (def subscription (on :ready (fn [msg]
                                   (prn  msg))))
    
    (broadcast [:ready true])
    (unsubscribe-to subscription))
  
  (let [[topic-chan kill-chan :as subscription] (subscribe-to topic)]
    (a/go-loop []
      (let [[v ch] (a/alts! [kill-chan topic-chan]
                            :priority true)]
        (if (= ch kill-chan)
          (unsubscribe-to subscription)
          (do
            (when-not (nil? v)
              (call-back-fn v))
            (recur)))))
    subscription))

(defn whenever
  "returns a closure that takes a call-back-fn which is executed when ever the topic message is broadcasted.
  the returned closure, returns a vector of subscriptions that can be passed to unsubscribe-to to remove event
  listeners"
  [topic]

  (comment ;;example using whenever
    (def when-ready (whenever :ready))

    (def subscriptions (when-ready (fn [msg]
                                     (prn "i'm ready " msg))))
    (count subscriptions)
    (map #(unsubscribe-to %) subscriptions)
    (broadcast [:ready true]))
  
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

(defn postpone
  "postpone the execution of execute-fn for ms milliseconds"
  [execute-fn ms]
  (a/go (a/<! (a/timeout ms))
        (execute-fn)))
