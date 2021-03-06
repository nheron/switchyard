/*
 * Copyright 2016 Red Hat Inc. and/or its affiliates and other contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.switchyard.common.property;

/** 
 * Common properties.
 *
 * @author <a href="mailto:david.virgil.naranjo@gmail.com">david.virgil.naranjo@gmail.com</a>
 */
public interface PropertyConstants {
    /** The RTGOV resubmission header ID. **/
    public static final String RTGOV_HEADER_RESUBMITTED_ID = "_rtgov_.*";
    /** The RTGOV resubmission header ID pattern. **/
    public static final String RTGOV_HEADER_RESUBMITTED_ID_PATTERN = "^" + RTGOV_HEADER_RESUBMITTED_ID + "$";

    /** The domain property propagate prefix constant. */
    public static final String DOMAIN_PROPERTY_PROPAGATE_REGEX = "org.switchyard.propagate.regex";
}
