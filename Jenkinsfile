/*
 * Copyright Terracotta, Inc.
 * Copyright IBM Corp. 2024, 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//Jenkins integration.
// safe to release on commit because we don't build PRs with Jenkins
mavenBuild.autoRelease(
    mavenBuild.getLegacy4xDefaults() // relying on mirror in settings.xml
    +
    [jdk:11, increment_pom_version_digit: -1] // increment last digit after release
) 



