/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.pattern;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import org.gradle.api.file.RelativePath;

import java.util.List;
import java.util.function.Predicate;

public class PatternMatcherFactory {

    private static final EndOfPathMatcher END_OF_PATH_MATCHER = new EndOfPathMatcher();
    private static final Splitter PATH_SPLITTER = Splitter.on(CharMatcher.anyOf("\\/"));
    private static final Predicate<RelativePath> MATCH_ALL = path -> true;

    public static Predicate<RelativePath> getPatternsMatcher(boolean partialMatchDirs, boolean caseSensitive, Iterable<String> patterns) {
        Predicate<RelativePath> predicate = MATCH_ALL;
        for (String pattern : patterns) {
            Predicate<RelativePath> patternMatcher = getPatternMatcher(partialMatchDirs, caseSensitive, pattern);
            predicate = predicate == MATCH_ALL
                ? patternMatcher
                : predicate.or(patternMatcher);
        }
        return predicate;
    }

    public static Predicate<RelativePath> getPatternMatcher(boolean partialMatchDirs, boolean caseSensitive, String pattern) {
        PathMatcher pathMatcher = compile(caseSensitive, pattern);
        return new PathMatcherBackedPredicate(partialMatchDirs, pathMatcher);
    }

    public static PathMatcher compile(boolean caseSensitive, String pattern) {
        if (pattern.length() == 0) {
            return END_OF_PATH_MATCHER;
        }

        // trailing / or \ assumes **
        if (pattern.endsWith("/") || pattern.endsWith("\\")) {
            pattern = pattern + "**";
        }
        List<String> parts = PATH_SPLITTER.splitToList(pattern);
        return compile(parts, 0, caseSensitive);
    }

    private static PathMatcher compile(List<String> parts, int startIndex, boolean caseSensitive) {
        if (startIndex >= parts.size()) {
            return END_OF_PATH_MATCHER;
        }
        int pos = startIndex;
        while (pos < parts.size() && parts.get(pos).equals("**")) {
            pos++;
        }
        if (pos > startIndex) {
            if (pos == parts.size()) {
                return new AnythingMatcher();
            }
            return new GreedyPathMatcher(compile(parts, pos, caseSensitive));
        }
        return new FixedStepPathMatcher(PatternStepFactory.getStep(parts.get(pos), caseSensitive), compile(parts, pos + 1, caseSensitive));
    }

    static class PathMatcherBackedPredicate implements Predicate<RelativePath> {
        private final boolean partialMatchDirs;
        private final PathMatcher pathMatcher;

        PathMatcherBackedPredicate(boolean partialMatchDirs, PathMatcher pathMatcher) {
            this.partialMatchDirs = partialMatchDirs;
            this.pathMatcher = pathMatcher;
        }

        PathMatcher getPathMatcher() {
            return pathMatcher;
        }

        @Override
        public boolean test(RelativePath element) {
            if (element.isFile() || !partialMatchDirs) {
                return pathMatcher.matches(element.getSegments(), 0);
            } else {
                return pathMatcher.isPrefix(element.getSegments(), 0);
            }
        }
    }
}
