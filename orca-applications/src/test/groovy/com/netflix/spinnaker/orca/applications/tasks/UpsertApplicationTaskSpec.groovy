/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.orca.applications.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class UpsertApplicationTaskSpec extends Specification {
  @Subject
  def task = new UpsertApplicationTask(mapper: new ObjectMapper())

  def config

  void setup() {
    config = [
      account    : "test",
      application: [
        "name" : "application",
        "owner": "owner",
        "repoProjectKey" : "project-key",
        "repoSlug" : "repo-slug",
        "repoType" : "github"
      ],
      user: "testUser"
    ]
  }

  void "should create an application in global registries"() {
    given:
    def app = new Application(config.application + [accounts: config.account, user: config.user])
    task.front50Service = Mock(Front50Service) {
      1 * get(config.application.name) >> null
      1 * create(app)
      1 * updatePermission(app.name, app.permission)
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  @Unroll
  void "should update existing application, using existing accounts (#accounts) if not supplied"() {
    given:
    config.application.accounts = accounts
    Application application = new Application(config.application + [
        user    : config.user
    ])
    Application existingApplication = new Application(
      name: "application", owner: "owner", description: "description", accounts: "prod,test"
    )
    task.front50Service = Mock(Front50Service) {
      1 * get(config.application.name) >> existingApplication
      // assert that the global application is updated w/ new application attributes and merged accounts
      1 * update("application", {it == application && it.accounts == expectedAccounts })
      1 * updatePermission(application.name, application.permission)
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.status == ExecutionStatus.SUCCEEDED

    where:
    accounts   || expectedAccounts
    null       || "prod,test"
    "test"     || "test"
  }

  @Unroll
  void "should keep track of previous and new state during #operation"() {
    given:
    Application application = new Application(config.application)
    application.accounts = config.account
    application.user = config.user

    task.front50Service = Mock(Front50Service) {
      1 * get(config.application.name) >> initialState
      1 * "${operation}"(*_)
      1 * updatePermission(*_)
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.stageOutputs.previousState == (initialState ?: [:])
    result.stageOutputs.newState == application

    where:
    initialState      | operation
    null              | 'create'
    new Application() | 'update'
  }
}
