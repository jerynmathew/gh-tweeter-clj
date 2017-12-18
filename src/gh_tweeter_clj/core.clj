(ns gh-tweeter-clj.core
    (:gen-class)
    (:require [clj-http.lite.client :as client])
    (:require [cemerick.url :refer (url url-encode)])
    (:require [clojure.data.json :as json])
    (:require [clojure.string :as str]))


(defn select-keys* [m paths]
  (into {} (map (fn [p]
                  [(last p) (get-in m p)]))
        paths))


(defn get-pr-details [dict]
    (select-keys* dict [["number"] ["title"] ["user" "login"]]))


(defn get-github-pr-url [repo-str]
    (format "https://api.github.com/repos/%s/pulls?state=open" repo-str))


(defn get-open-prs [github-url]
    (map get-pr-details 
        (json/read-str
            ((client/get github-url {:basic-auth "jerynmathew:74dfc83ddfa7f57796c5781eedc31ee7797b9389"}) :body))))


(defn get-next-url [github-url]
    (second 
        (re-find 
            #"<(.*)>;\s+rel=\"next\"" 
            (((client/head github-url {:basic-auth "jerynmathew:74dfc83ddfa7f57796c5781eedc31ee7797b9389"}) :headers) "link")))
    )


;recursive loop to create list of new open prs
(defn get-new-prs [repo-str last-pr-id]
    (loop [url (get-github-pr-url repo-str)
           prs []]

        (let [all-prs (get-open-prs url)
              new-prs (filter #(> (% "number") last-pr-id) all-prs)
              next-url (get-next-url url)]

            (if-not (and next-url 
                        (= (count all-prs) 
                            (count new-prs)))
                (concat prs new-prs)
                (recur next-url
                       (concat prs new-prs))))))



(defn post-tweet [twitter-handle message]
    )

(defn search-tweet [twitter-handle]
    )