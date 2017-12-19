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


(defn test-oauth []
    (def consumer 
        (oauth/make-consumer "JqksaXQ93obirTYhf63GMUHyD"
                             "vhDReUCjo8QX5p80MI5noh5b5cxGY7cbEBF1GXM0C1zAcMNbau"
                             "https://api.twitter.com/oauth/request_token"
                             "https://api.twitter.com/oauth/access_token"
                             "https://api.twitter.com/oauth/authorize"
                             :hmac-sha1))
    (def request-token (oauth/request-token consumer))

    (def approval-resp (oauth/user-approval-uri consumer 
                            (:oauth_token request-token)))
    (println "Approval Resp: " approval-resp)

    ;; without verifier
    ;(def access-token-response (oauth/access-token consumer request-token))
    ;(println "AccessTokenResp: " access-token-response)

    ; (def user-params {:screen_name "jer_matt"})
    (def user-params {:status "Greeting Twitter! Posting from #clojure with #oauth."})

    ; (def twurl "https://api.twitter.com/1.1/statuses/user_timeline.json")
    (def twurl "https://api.twitter.com/1.1/statuses/update.json")

    (def credentials (oauth/credentials consumer
                                        (:oauth_token "942359695594987520-hAQ061y3BJL0eYmyt9QMvftuMLmQqnF")
                                        (:oauth_token_secret "c2rjFSRQJJ6TbsLTPjbV22O9LrozAddgaxakf8qGHLnVj")
                                        :POST
                                        twurl
                                        user-params))

    (client/post twurl {:query-params (merge credentials user-params)})
)


(defn post-tweet [twitter-handle message]
    )

(defn search-tweet [twitter-handle]
    )
