/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.runtimes.builder.buildsteps;

import com.google.cloud.runtimes.builder.Constants;
import com.google.cloud.runtimes.builder.buildsteps.base.BuildStep;
import com.google.cloud.runtimes.builder.buildsteps.base.BuildStepException;
import com.google.cloud.runtimes.builder.config.domain.BuildContext;
import com.google.cloud.runtimes.builder.injection.GradleDockerImage;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Build step that invokes gradle.
 */
public class GradleBuildStep implements BuildStep {

  private final Logger logger = LoggerFactory.getLogger(GradleBuildStep.class);

  private final String gradleImage;

  @Inject
  GradleBuildStep(@GradleDockerImage String gradleImage) {
    this.gradleImage = gradleImage;
  }

  @Override
  public void run(BuildContext buildContext) throws BuildStepException {
    buildContext.getDockerfile()
        .appendLine("FROM " + gradleImage + " as " + Constants.DOCKERFILE_BUILD_STAGE)
        .appendLine("ADD . .")
        .appendLine("RUN " + getGradleExecutable(buildContext) + " build")
        .appendLine();

    buildContext.setBuildArtifactLocation(Optional.of(Paths.get("build/libs")));
  }

  private String getGradleExecutable(BuildContext buildContext) {
    Path wrapperPath = buildContext.getWorkspaceDir().resolve("gradlew");
    if (Files.exists(wrapperPath)) {
      Path relativePath = buildContext.getWorkspaceDir().relativize(wrapperPath);
      logger.info("Gradle wrapper discovered at {}. Using wrapper instead of system gradle.",
          relativePath);

      return "./" + relativePath.toString();
    }
    return "gradle";
  }
}
