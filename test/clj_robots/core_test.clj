(ns clj-robots.core-test
  (:use
    [clj-robots.core]
    [clj-robots.utils :only (get-lines)]
    [clj-robots.test.utils :only (refer-private)]
    [clojure.test])
  (:gen-class))

(refer-private 'clj-robots.core)

(deftest test-trim-comment
  (is (= "hello there " (trim-comment "hello there #this is a comment!"))))

(deftest test-parse-line
  (is (= [:user-agent ":*:"]
         (parse-line "UsEr-AgEnt: :*:# This is a comment")))
  (is (nil? (parse-line "user-agent*")))
  (is (nil? (parse-line ""))))

(deftest test-parse-lines
  (let [lines (get-lines "clj_robots/robots.txt")
        expected {:request-rate 1/300
                  :crawl-delay 10
                  :robot-version "Version 2.0"
                  :sitemap ["http://www.lousycoder.com/sitemap1.xml"
                            "http://www.lousycoder.com/sitemap2.xml"]
                  "*"
                    [[:allow "/images/foo.jpg"]
                     [:disallow "/cgi-bin/"]
                     [:disallow "/images/"]
                     [:disallow "/tmp/"]
                     [:disallow "/private/"]]
                  "google"
                    [[:allow "/bif/baz/boo/"]
                     [:disallow "/moo/goo/too/"]]
                  "foo"
                    [[:disallow "/mif/tif/psd/"]
                     [:allow "/gif/png/img/"]]
                  "bar"
                    [[:disallow "/mif/tif/psd/"]
                     [:allow "/gif/png/img/"]]
                  "baz"
                    [[:disallow "/mif/tif/psd/"]
                     [:allow "/gif/png/img/"]]}
        ds (parse-lines lines)
        result (dissoc ds :modified-time)]
    (is (contains? ds :modified-time))
    (is (= expected result))))

(deftest test-parse-lines-bad
  (let [lines (get-lines "clj_robots/robots-bad.txt")
        ds (parse-lines lines)
        expected {"*" [[:allow "/foobar/"]]}
        result (dissoc ds :modified-time)]
    (is (= expected result))))

(deftest test-parse
  (is (nil? (parse nil)))
  (is {"*" []} (dissoc (parse "") :modified-time))
  (is {"*" []} (dissoc (parse [""]) :modified-time))
  (is {"*" []} (dissoc (parse (get-lines "clj_robots/empty.txt"))
                 :modified-time))
  (is (contains? (parse (get-lines "clj_robots/robots.txt")) "google")))

(deftest test-crawlable?
  (let [ds {"google"
              [[:disallow "/foo/"]
               [:disallow "/bar/"]
               [:allow "/bar/baz.html"]
               [:disallow "/bar/baz/"]]
            "*"
              [[:disallow "/bar/"]
               [:allow "/bif/"]
               [:disallow "bif/bof/"]]}]
    (is (not (crawlable? ds "/foo/" :user-agent "google")))
    (is (not (crawlable? ds "/bar/" :user-agent "google")))
    (is (not (crawlable? ds "/bar/" :user-agent "*")))
    (is (not (crawlable? ds "/bar/bif.html" :user-agent "google")))
    (is (not (crawlable? ds "/bar/baz.html" :user-agent "google")))
    (is (crawlable? ds "/foo/bar.html"))
    (is (crawlable? ds "/foo/" :user-agent "*")))
  (let [ds {"*" [[:disallow "/foo/"] [:disallow "/bar/"]]}]
    (is (crawlable? ds "/foo"))
    (is (crawlable? ds "/bif/"))
    (is (not (crawlable? ds "/bar/")))
    (is (not (crawlable? ds "/bar/2.html")))))

(deftest test-process-request-rate
  (is (= (process-request-rate "1/10") 1/10))
  (is (= (process-request-rate "1/10h") 1/36000))
  (is (= (process-request-rate "1/10m") 1/600))
  (is (= (process-request-rate "1/10s") 1/10)))
