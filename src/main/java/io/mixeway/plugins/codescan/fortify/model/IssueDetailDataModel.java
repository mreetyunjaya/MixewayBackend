package io.mixeway.plugins.codescan.fortify.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IssueDetailDataModel {
    @JsonProperty(value = "data")
    IssueDetailModel issueDetailModel;

    public IssueDetailModel getIssueDetailModel() {
        return issueDetailModel;
    }

    public void setIssueDetailModel(IssueDetailModel issueDetailModel) {
        this.issueDetailModel = issueDetailModel;
    }
}
