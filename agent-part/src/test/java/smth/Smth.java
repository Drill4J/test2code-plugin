/**
 * Copyright 2020 EPAM Systems
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package smth;

import java.util.HashMap;
import java.util.Map;

public class Smth implements Runnable {


    private final String owners;

    public Smth(){
        owners = "null";
    }
    public Smth(String clinicService) {
        this.owners = clinicService;
    }

    public String processFindForm(String owner, Map<String, Object> model) {

        // allow parameterless GET request for /owners to return all records
        if (owner == null) {
            owner = (""); // empty string signifies broadest possible search
        }

        // find owners by last name
        boolean results = !this.owners.contains(owner);
        if (!results) {
            // no owners found
//            result.rejectValue("lastName", "notFound", "not found");
            return "owners/findOwners";
        } else if (results && owner.equals("1")) {
            // 1 owner found
            owner = String.valueOf(results) + owner;
            return "redirect:/owners/" + owner;
        } else {
            // multiple owners found
            model.put("selections", results);
            return "owners/ownersList";
        }
    }

    @Override
    public void run() {
        processFindForm("sda", new HashMap<String, Object>());
    }
}
