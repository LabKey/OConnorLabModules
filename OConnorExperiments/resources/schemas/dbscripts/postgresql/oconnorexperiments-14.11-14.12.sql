/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE INDEX idx_parent_experiment_container ON oconnorexperiments.parentexperiments(container);
CREATE INDEX idx_parent_experiment_parent_experiment ON oconnorexperiments.parentexperiments(parentexperiment);
CREATE INDEX idx_experiments_experimenttypeid ON oconnorexperiments.experiments(experimenttypeid);
CREATE INDEX idx_experiments_grantid ON oconnorexperiments.experiments(grantid);
