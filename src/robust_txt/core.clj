(ns robust-txt.core
  (:require
    [robust-txt.utils :as util]
    [clojure.contrib.io :as io]
    [clojure.contrib.str-utils2 :as su]
    [clj-http.client :as client])
  (:import
    [clojure.lang Sequential]
    [java.io InputStream]
    [java.net URL])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn trim-comment
  [line]
  (su/replace line #"#.*$" ""))

(defn process-user-agent
  [directives user-agent value]
  (dosync
    (ref-set user-agent value)
    (alter directives assoc @user-agent [])))

(defn process-allow
  [directives user-agent value]
  (dosync
    (let [permissions (@directives @user-agent)]
      (alter directives
             assoc @user-agent (vec (conj permissions [:allow value]))))))

(defn process-disallow
  [directives user-agent value]
  (dosync
    (let [permissions (@directives @user-agent)]
      (alter directives
             assoc @user-agent (vec (conj permissions [:disallow value]))))))

(defn parse-line
  [line]
  (let [[left right]  (su/split (trim-comment line) #":" 2)
        key (keyword  (su/lower-case (su/trim left)))
        trimmed-value (if (nil? right) "" (su/trim right))
        value         (if (contains? #{:crawl-delay :request-rate} key)
                        (try (Integer/parseInt trimmed-value)
                          (catch NumberFormatException e ""))
                        trimmed-value)]
    (if (= "" value) nil [key value])))

(defn parse-lines
  [lines]
  (let [user-agent (ref "*")
        directives (ref {"*" []})]
    (do
      (doseq [line lines]
        (let [[key value] (parse-line line)]
          (cond
            (or (nil? key) (nil? value))
              nil
            (= key :user-agent)
              (process-user-agent directives user-agent value)
            (= key :allow)
              (process-allow directives user-agent value)
            (= key :disallow)
              (process-disallow directives user-agent value)
            :default
              (dosync
                (alter directives assoc key value)))))
      (dosync
        (alter directives assoc :modified-time (System/currentTimeMillis)))
      @directives)))

(defn get-robots
  [url]
  (try
    (let [domain (.getHost ^URL (io/as-url url))
          response (client/get (str "http://" domain "/robots.txt"))]
      (response :body))
    (catch Exception e "")))

(defn crawlable-by-standard?
  [directives path & {:keys [user-agent] :or {user-agent "*"}}]
  (let [permissions (filter #(= :disallow (first %))
                            (get directives user-agent))]
    (nil? (some #(.startsWith ^String path (last %)) permissions))))

(defn crawlable-by-google?
  [directives path & {:keys [user-agent] :or {user-agent "*"}}]
  (throw (new UnsupportedOperationException "Method not implemented")))

(defn crawlable-by-bing?
  [directives path & {:keys [user-agent] :or {user-agent "*"}}]
  (throw (new UnsupportedOperationException "Method not implemented")))

(defn crawlable?
  [directives path & {:keys [user-agent strategy]
                      :or {user-agent "*" strategy :standard}}]
  (cond
    (= strategy :google)
      (and (crawlable-by-google? directives path :user-agent "*")
           (crawlable-by-google? directives path :user-agent user-agent))
    (= strategy :bing)
      (and (crawlable-by-bing? directives path :user-agent "*")
           (crawlable-by-bing? directives path :user-agent user-agent))
    :default
      (and (crawlable-by-standard? directives path :user-agent "*")
           (crawlable-by-standard? directives path :user-agent user-agent))))

(defmulti parse-robots class)

(defmethod parse-robots
  Sequential [lines]
  (parse-lines lines))

(defmethod parse-robots
  String [string]
  (parse-robots (su/split-lines string)))

(defmethod parse-robots
  InputStream [stream]
  (parse-robots (util/stream-to-string stream)))
