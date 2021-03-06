(ns flightgear.core
  (:require [clojure.java.io :as io])
  (:require [clojure.zip :as zip])
  (:require [clojure.xml :as xml])
  (:import [java.net Socket]
           [java.io PrintWriter ByteArrayInputStream]))

(def connection (atom nil))
(def in (atom nil))
(def out (atom nil))

(defn send-message [msg]
  (doto @out
    (.print (str msg "\r\n"))
    (.flush))
  nil)

(defn connect [host port]
  (println "Connecting to" host "on" port)
  (let [s (Socket. host port)]
    (reset! connection s)
    (reset! in (io/reader (.getInputStream s)))
    (reset! out (PrintWriter. (.getOutputStream s)))
    (send-message "data")
    true))

(defn get-property-list []
  (apply str (concat
               (take-while #(not (= "</PropertyList>" %)) (repeatedly #(.readLine @in)))
               "</PropertyList>")))

(defn property-node-to-entry [node]
  (let [tag (node :tag)
        type ((node :attrs) :type)
        content (first (node :content))]
    (cond
      (#{"double" "bool" "int"} type) [tag (read-string content)]
      :default [tag content])))

(defn property-list-to-map [property-list]
  (let [list-bytes (ByteArrayInputStream. (.getBytes property-list))
        tree (zip/xml-zip (xml/parse list-bytes))
        properties (zip/children tree)]
    (into {} (map property-node-to-entry properties))))

(defn request-property-list [property]
  (send-message (str "dump " property))
  (property-list-to-map (get-property-list)))

(defn set-property [property value]
  (send-message (str "set " property " " value)))

(defn position []
  (request-property-list "/position"))

(defn orientation []
  (request-property-list "/orientation"))

(defn velocities []
  (request-property-list "/velocities"))

(defn flight-controls []
  (request-property-list "/controls/flight"))

(defn engine-controls
  ([] (engine-controls 1))
  ([n] (request-property-list (str "/controls/engines/engine[" n "]"))))

(defn starter! [value]
  (set-property "/controls/switches/starter" (if value "true" "false")))

(defn aileron! [value]
  (set-property "/controls/flight/aileron" value))

(defn elevator! [value]
  (set-property "/controls/flight/elevator" value))

(defn rudder![value]
  (set-property "/controls/flight/rudder" value))

(defn flaps! [value]
  (set-property "/controls/flight/flaps" value))

(defn throttle! [value]
  (set-property "/controls/engines/engine/throttle" value))