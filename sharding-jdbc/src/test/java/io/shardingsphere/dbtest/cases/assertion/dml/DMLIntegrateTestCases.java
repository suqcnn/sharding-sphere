/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
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
 * </p>
 */

package io.shardingsphere.dbtest.cases.assertion.dml;

import io.shardingsphere.dbtest.cases.assertion.root.IntegrateTestCases;
import lombok.Getter;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.LinkedList;
import java.util.List;

/**
 * JAXB definition of DML integrate test cases.
 * 
 * @author zhangliang 
 */
@Getter
@XmlRootElement(name = "integrate-test-cases")
public class DMLIntegrateTestCases implements IntegrateTestCases {
    
    @XmlElement(name = "dml-test-case")
    private List<DMLIntegrateTestCase> integrateTestCases = new LinkedList<>();
}
