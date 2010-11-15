(ns robust-txt.core
  (:require
    [robust-txt.utils :as util]
    [clojure.contrib.io :as io]
    [clojure.contrib.str-utils2 :as su])
  (:gen-class))

(def int-keys #{"crawl-delay" "request-rate"})

(defn trim-comment
  [line]
  (su/replace line #"#.*$" ""))


(defn process-user-agent
  [result user-agent value]
  (dosync
    (ref-set user-agent value)
    (alter result assoc @user-agent [])))

(defn process-allow
  [result user-agent value]
  (dosync
    (let [permissions (@result @user-agent)]
      (alter result
             assoc @user-agent (vec (conj permissions [:allow value]))))))

(defn process-disallow
  [result user-agent value]
  (dosync
    (let [permissions (@result @user-agent)]
      (alter result
             assoc @user-agent (vec (conj permissions [:disallow value]))))))

(defn process-value
  [value]
  (if (contains? #{"crawl-delay" "request-rate"} value)
    (try (Integer/parseInt value)
      (catch NumberFormatException e nil))
    value))

(defn process-directive
  [result key value]
  (let [processed-value (process-value value)]
    (if (nil? processed-value)
      nil
      (dosync
        (alter result assoc key processed-value)))))

(defn parse-line
  [line]
  (let [[key value] (su/split (trim-comment line) #":" 2)]
    (if (nil? value)
      nil
      [(su/lower-case (su/trim key)) (su/trim value)])))

(defn parse-lines
  [lines]
  (let [user-agent (ref "*")
        result (ref {})]
    (do
      (doseq [line lines]
        (let [[key value] (parse-line line)]
          (cond
            (or (nil? key) (nil? value))
              nil
            (= key "user-agent")
              (process-user-agent result user-agent value)
            (= key "allow")
              (process-allow result user-agent value)
            (= key "disallow")
              (process-disallow result user-agent value)
            :default
              (process-directive result key value))))
      (dosync
        (alter result assoc "modified-time" (System/currentTimeMillis)))
      result)))
