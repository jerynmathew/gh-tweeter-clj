# gh-tweeter-clj
Simple application that reads open PRs from a given (input) repository and tweets it to a user (input) twitter account.

## Description
Application will first create a state management file locally - "last-pr-id.json". Its a key-value pair of repo-names to the last open Pull Request ID the application encountered; the default value being 0.

Using this information, it will query github for all *open* PRs greater than this PR id (because PR ids are sequential). It will make use of the pagination apis where necessary.

If there are new PRs found, it will create tweet messages for the same, generate oAuth tokens for twitter, and sequential post each tweet to the user's twitter account.

## Prerequisite
* Take a pull of this repository
* Create a file "config.json", based on the template "config.json.template". Add the github and twitter access tokens here.


## Usage:
lein trampoline run -c ./config.json <repo-name>

### Example
lein trampoline run -c ./config.json clojure/clojure


## FIXME
* Store the oAuth access token/refresh token locally and reuse them
* Get rid of clj-oauth library and manually generate tokens. Watch out for percent-encoding.
* Try to fetch state from previous tweets, rather than locally storing state.
