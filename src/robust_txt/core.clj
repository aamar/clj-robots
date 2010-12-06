(ns robust-txt.core
  (:require
    [robust-txt.utils :as utils]
    [clojure.contrib.io :as io]
    [clojure.contrib.str-utils2 :as su]
    [clj-http.client :as client])
  (:import
    [clojure.lang Sequential]
    [java.io InputStream]
    [java.net URL])
  (:gen-class))

;(set! *warn-on-reflection* true)

(defn- trim-comment
  "Removes everything after the first # character in a String."
  [line]
  (su/replace line #"#.*$" ""))

(defn- int-key?
  "Returns true if this directive's value should be parsed as an Integer."
  [key]
  (contains? #{:crawl-delay :request-rate} key))

(defn- process-user-agent
  "Set the current user-agent and add it to the list of user-agents."
  [directives user-agent value]
  (dosync
    (ref-set user-agent value)
    (alter directives assoc @user-agent [])))

(defn- process-permission
  "Set an allow or disallow directive for the current user-agent."
  [directives user-agent key value]
  (dosync
    (let [permissions (@directives @user-agent)]
      (alter directives
             assoc @user-agent (vec (conj permissions [key value]))))))

(defn- parse-key
  "Parse the key in a directive."
  [key]
  (keyword (su/lower-case (su/trim key))))

(defn- parse-value
  "Parse the value in a directive."
  [key value]
  (cond (nil? value)   ""
        (int-key? key) ((comp utils/parse-int su/trim) value)
        :default       (su/trim value)))

(defn- parse-line
  "Parse a line from a robots.txt file."
  [line]
  (let [[left right]  (su/split (trim-comment line) #":" 2)
        key           (parse-key left)
        value         (parse-value key right)]
    (if (= "" value) nil [key value])))

(defn- parse-lines
  "Parse the lines of the robots.txt file."
  [lines]
  (let [user-agent (ref "*")
        directives (ref {"*" []})]
    (doseq [line lines]
      (let [[key value] (parse-line line)]
        (cond
          (or (nil? key) (nil? value))
            nil
          (= key :user-agent)
            (process-user-agent directives user-agent value)
          (contains? #{:allow :disallow} key)
            (process-permission directives user-agent key value)
          :default
            (dosync (alter directives assoc key value)))))
    (dosync
      (alter directives assoc :modified-time (System/currentTimeMillis)))
    @directives))

(defn get-robots
  "Download robots.txt for a particular URL."
  [^URL url]
  (try
    (let [protocol (.getProtocol url)
          domain (.getHost url)
          response (client/get (str protocol "://" domain "/robots.txt"))]
      (response :body))
    (catch Exception e "")))

(defn crawlable?
  "Returns true if a list of directives allows the path to be crawled using
  this interpretation of robots.txt:

  http://www.robotstxt.org/

  Note that allow directives are completely ignored and only the first
  disallow directive is consulted to determine if a path can be crawled."
  [directives ^String path & {:keys [user-agent] :or {user-agent "*"}}]
  (let [select-disallows #(= :disallow (first %))
        permissions (filter select-disallows (get directives user-agent))]
    (and (nil? (some #(.startsWith path (last %)) permissions))
         (if (not= "*" user-agent)
           (crawlable? directives path :user-agent "*")
           true))))

(defmulti parse-robots
  "Parse robots.txt; returns a data structure to pass to crawlable?"
  class)

(defmethod parse-robots
  Sequential [lines]
  (parse-lines lines))

(defmethod parse-robots
  String [string]
  (parse-robots (su/split-lines string)))

(defmethod parse-robots
  InputStream [stream]
  (parse-robots (utils/stream-to-string stream)))
