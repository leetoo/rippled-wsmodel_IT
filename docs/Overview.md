# Overview of rippled-test

Ripple servers natively support a websocket communication model. 
There are four primary types of interaction supported:
1. Commands -- These are issued and take effect or fail immediately.
2. Admin Commands -- Commands that require server authentication, typically must own the rippled server
3. Subscriptions -- These are queries that request the rippled server to respond with a long running stream of 
information messages, e.g. subscribing to notifications/events whenever a ledger is committed
4. Transactions -- These are transactions because they are multistep actions are require signing/authentication.

See: https://developers.ripple.com/ for general documentation.


This systems has a lot of model objects to model the wireprotocal of messages sent over the websocket, in addition to
 some utility routines.
 
Note that the tests here are somewhat naive unit tests. Integration testing with local, testnet and production servers 
is in rippled-devtest project.
 
 
