# Oauth Server

This service is the authorized authentication center of the Choerodon Microservices Framework and is mainly responsible for user privilege and authorization.

## Feature

Add addition authorized login function (WeChat login, etc.)
## Requirements

The `oauth-server` depends on the [iam-service](https://github.com/choerodon/iam-service) database, so make sure that the `iam-service` database is initialized before using it.

## Installation and Getting Started

* database：
The `iam-service` database of the used Choerodon Microservices Framework.

* Then run the project in the root directory of the project：
```sh
mvn spring-boot:run
```

## Usage

1. User login authorization :
    * The user completes the authorization in oauth through the username and password.
    * Oauth will produce an `access_token` based on the user and the authenticated client, and save it to `tokenStore`.
1. Access Resource Service Certification for user :
    * The user requests carrying the `access_token`. After the oauth finishes checking, the request is forwarded by the gateway to the corresponding resource service.
    * Return a 401 error for user request illegally and jumps to the login page to reauthorize.


## Dependencies

* MySQL
* Kafka

## Links

* [Change Log](./CHANGELOG.zh-CN.md)

## Reporting Issues

If you find any shortcomings or bugs, please describe them in the [issue](https://github.com/choerodon/choerodon/issues/new?template=issue_template.md).
    
## How to Contribute
Pull requests are welcome! [Follow](https://github.com/choerodon/choerodon/blob/master/CONTRIBUTING.md) to know for more information on how to contribute.

