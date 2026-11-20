/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
package pit12.runtime.languages;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import pit12.shared.concurrent.ClientThread;

public final class LanguagesTest {
    @Test
    public void formatsParametersInPlaceholderOrder() {
        Languages languages = new Languages(ClientThread.current());
        assertEquals("bar <- foo", languages.format("{1} <- {0}", "foo", "bar"));
    }

    @Test
    public void notifiesListenersOfTheCurrentLanguageOnRegistrationAndChange() {
        Languages languages = new Languages(ClientThread.current());
        String otherLocale = languages.names().keySet().stream()
                .filter(locale -> !"en-us".equals(locale)).findFirst().get();
        List<String> observed = new ArrayList<>();
        languages.addListener(() -> observed.add(languages.locale()));
        languages.select(otherLocale);
        languages.select("en-us");
        assertEquals(Arrays.asList("en-us", otherLocale, "en-us"), observed);
    }
}
