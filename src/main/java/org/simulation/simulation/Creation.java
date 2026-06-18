package org.simulation.simulation;

import java.util.ArrayList;
import java.util.List;

public class Creation {

    public static List<Provider> createProviders() {
        List<Provider> providers = new ArrayList<>();
        providers.add(new AWSProvider());
        providers.add(new AzureProvider());
        providers.add(new GoogleProvider());

        for (Provider p : providers) {
            p.printConfig();
        }

        return providers;
    }
}

