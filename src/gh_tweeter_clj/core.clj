(ns gh-tweeter-clj.core
    (:gen-class)
    (:require [clj-http.lite.client :as client])
    (:require [cemerick.url :refer (url url-encode)])
    (:require [clojure.data.json :as json])
    (:require [clojure.string :as str])
    (:require [oauth.client :as oauth])
    (:use [slingshot.slingshot :only [throw+ try+]]))


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


(defn create-search-credentials [consumer-key
                                 consumer-secret]
    (oauth/make-consumer consumer-key
                         consumer-secret
                         "https://api.twitter.com/oauth/request_token"
                         "https://api.twitter.com/oauth/access_token"
                         "https://api.twitter.com/oauth/authorize"
                         :hmac-sha1))


(defn create-post-credentials [consumer-key
                               consumer-secret]
    (let [consumer (create-search-credentials consumer-key consumer-secret)
          request-token (oauth/request-token consumer)
          approval-resp (oauth/user-approval-uri consumer (:oauth_token request-token))]

        (println "Click on this link and copy the PIN: " approval-resp)
        (print "Enter the PIN: ") (flush)
        (def verifier-pin (read-line))

        {:consumer consumer
         :access-token (oauth/access-token consumer request-token verifier-pin)}))


(defn post-tweet [message 
                  oauth-consumer 
                  bearer-token]
    (let [user-params {:status message}
          twurl "https://api.twitter.com/1.1/statuses/update.json"
          credentials (oauth/credentials oauth-consumer
                                        (:oauth_token bearer-token)
                                        (:oauth_token_secret bearer-token)
                                        :POST
                                        twurl
                                        user-params)]
        (json/read-str 
            ((client/post twurl {:query-params (merge credentials user-params)}) :body))))


(defn search-tweet [twitter-handle
                    oauth-consumer
                    access-token
                    access-token-secret]

    (let [user-params {:screen_name twitter-handle}
          twurl "https://api.twitter.com/1.1/statuses/user_timeline.json"
          credentials (oauth/credentials oauth-consumer
                                        (:oauth_token access-token)
                                        (:oauth_token_secret access-token-secret)
                                        :GET
                                        twurl
                                        user-params)]
        (json/read-str 
            ((client/get twurl {:query-params (merge credentials user-params)}) :body))))
