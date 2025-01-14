/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.agent.plugin.jakartaservlet._;

import org.glowroot.agent.plugin.api.checker.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Strings {

    private Strings() {}

    public static String nullToEmpty(@Nullable String string) {
        return string == null ? "" : string;
    }

    public static List<String> split(String string, char c) {
        List<String> list = new ArrayList<String>();
        int lastFoundIndex = -1;
        int nextFoundIndex;
        while ((nextFoundIndex = string.indexOf(c, lastFoundIndex + 1)) != -1) {
            String value = string.substring(lastFoundIndex + 1, nextFoundIndex).trim();
            if (!value.isEmpty()) {
                list.add(value);
            }
            lastFoundIndex = nextFoundIndex;
        }
        String value = string.substring(lastFoundIndex + 1).trim();
        if (!value.isEmpty()) {
            list.add(value);
        }
        return list;
    }
}
