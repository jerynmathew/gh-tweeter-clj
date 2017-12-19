(ns gh-tweeter-clj.core
    (:gen-class)
    (:require [clj-http.lite.client :as client])
    (:require [clojure.data.json :as json])
    (:require [clojure.string :as str])
    (:require [clojure.java [io :as io]])
    (:require [oauth.client :as oauth])
    (:require [clojure.tools.cli :refer [parse-opts]]))


(defn usage 
    "Return a Usage string to be printed later"
    [options-summary]
    (->> ["The application fetches latest open PRs from given github-repo and tweets it to given twitter account"
          ""
          "Usage: program-name [options] github-repo-name"
          ""
          "Options:"
          options-summary
          ""]
          (str/join \newline)))


(defn error-msg 
    "Print 'errors' message"
    [errors]
    (str "The following errors occurred while parsing your command:\n\n"
        (str/join \newline errors)))


(defn exit 
    "Exit application with 'status' code AFTER printing 'msg' on screen"
    [status msg]
    (println msg)
    (System/exit status))


(def cli-options [["-c" "--config-file" :required "JSON Config file containing credentials, twitter handle, github repo" 
                                        :parse-fn #(str %)]
                  ["-h" "--help" ]])


(defn select-keys* [m paths]
  (into {} (map (fn [p]
                  [(last p) (get-in m p)]))
        paths))


(defn get-pr-details [dict]
    (select-keys* dict [["number"] ["title"]]))


(defn get-github-pr-url [repo-str]
    (format "https://api.github.com/repos/%s/pulls?state=open" repo-str))


(defn check-gh-url [github-url auth-token]
    (< ((client/head github-url {:basic-auth auth-token :throw-exceptions false}) :status ) 299))


(defn get-open-prs [github-url auth-token]
    (if (check-gh-url github-url auth-token)
        (map get-pr-details 
            (json/read-str
                ((client/get github-url {:basic-auth auth-token}) :body)))
        (exit 1 "This Github Repo cannot be found!")))


(defn get-next-url [github-url auth-token]
    (second 
        (re-find 
            #"<(.*)>;\s+rel=\"next\"" 
            (((client/head github-url {:basic-auth auth-token}) :headers) "link")))
    )


;recursive loop to create list of new open prs
(defn get-new-prs [repo-str last-pr-id auth-token]
    (loop [url (get-github-pr-url repo-str)
           prs []]

        (let [all-prs (get-open-prs url auth-token)
              new-prs (filter #(> (% "number") last-pr-id) all-prs)
              next-url (get-next-url url auth-token)]

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
        (client/post twurl {:query-params (merge credentials user-params)}) :body))


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
        (client/get twurl {:query-params (merge credentials user-params)}) :body))


(defn update-last-used-pr [repo-name pr-id]
    (or 
        (spit "./last-pr-id.json" 
            (merge (json/read-str (slurp "./last-pr-id.json")) {repo-name pr-id}))) pr-id)


(defn get-last-known-pr [repo-name]
    (if (.exists (io/as-file "./last-pr-id.json"))
        (let [latest-pr-id ((json/read-str (slurp "./last-pr-id.json")) repo-name)]
          (if (nil? latest-pr-id)
            (update-last-used-pr repo-name 0)
            latest-pr-id))
        (or (spit "./last-pr-id.json" (json/write-str {repo-name 0})) 0)))


(defn gen-tweets-from-prs [repo-name pr-map]
    (let [{number "number"
           title "title"} pr-map]
        (def tweet (format "%s [%s] %s" repo-name number title))
        (subs tweet 0 (min (count tweet) 280) )))


(defn -main 
    [& args]

    ;; Setup args
    (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
        ;; Handle help and error conditions
        (cond
            ;; Handle '-h'
            (:help options) (exit 0 (usage summary))
            ;; Handle '-c'
            (empty? (:config-file options)) (exit 1 (error-msg ["-c/--config-file option is required"]))
            ;; Handle errors
            errors (exit 1 (error-msg errors))
            ;; Handle args 
            (< (count arguments) 1) ((exit 1 (usage summary))))
        
        (def repo-name (first arguments))
        (def config (json/read-str (slurp (:config-file options))))
        (println "Last known PR: " (get-last-known-pr repo-name))

        ;; Fetch details of PRs 
        (def results (get-new-prs repo-name (get-last-known-pr repo-name) (config "github_basic_auth_token")))

        ;; Update latest PR into local state
        (if-not (empty? results)
            (update-last-used-pr repo-name ((first results) "number"))))

        ;; generate tweets
        (def tweets (map #(gen-tweets-from-prs repo-name %) results))

        (if (= (count tweets) 0)
            (exit 0 (format "No new tweets to post for %s repo!" repo-name)))

        (def credentials
            (let [{c-key "twitter-consumer-key"
                   c-secret "twitter-consumer-secret"} config]
                (create-post-credentials c-key c-secret)))

        (let [{:keys [consumer access-token]} credentials]
            (doseq [tweet tweets] 
                (post-tweet tweet consumer access-token)))

    ;; Print Results and exit 0
    (exit 0 (format "Posted %s Tweets" (count tweets))))
