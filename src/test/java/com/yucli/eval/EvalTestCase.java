package com.yucli.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalTestCase {
    private String id;
    private String instruction;
    private String setupScript;
    private String verifyScript;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public String getSetupScript() { return setupScript; }
    public void setSetupScript(String setupScript) { this.setupScript = setupScript; }

    public String getVerifyScript() { return verifyScript; }
    public void setVerifyScript(String verifyScript) { this.verifyScript = verifyScript; }
}
