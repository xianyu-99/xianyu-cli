package com.yucli.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalTestCase {
    private String id;
    private String mode;
    private String instruction;
    private String setupScript;
    private String setupScriptWindows;
    private String setupScriptUnix;
    private String verifyScript;
    private String verifyScriptWindows;
    private String verifyScriptUnix;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public String getSetupScript() { return setupScript; }
    public void setSetupScript(String setupScript) { this.setupScript = setupScript; }

    public String getSetupScriptWindows() { return setupScriptWindows; }
    public void setSetupScriptWindows(String setupScriptWindows) { this.setupScriptWindows = setupScriptWindows; }

    public String getSetupScriptUnix() { return setupScriptUnix; }
    public void setSetupScriptUnix(String setupScriptUnix) { this.setupScriptUnix = setupScriptUnix; }

    public String getVerifyScript() { return verifyScript; }
    public void setVerifyScript(String verifyScript) { this.verifyScript = verifyScript; }

    public String getVerifyScriptWindows() { return verifyScriptWindows; }
    public void setVerifyScriptWindows(String verifyScriptWindows) { this.verifyScriptWindows = verifyScriptWindows; }

    public String getVerifyScriptUnix() { return verifyScriptUnix; }
    public void setVerifyScriptUnix(String verifyScriptUnix) { this.verifyScriptUnix = verifyScriptUnix; }
}
