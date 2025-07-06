function calculatePug() {
  try {
    var locationNode = guideBridge.resolveNode("location");
    var maritalStatusNode = guideBridge.resolveNode("maritalStatus");
    var incomeNode = guideBridge.resolveNode("income");
    var spouseIncomeNode = guideBridge.resolveNode("spouseIncome");

    var location = locationNode ? ("" + locationNode.value).trim() : "";
    if (location !== "Urban" && location !== "Rural") {
      console.log("[calculatePug] Invalid or missing 'location':", location);
      return false;
    }

    var maritalStatus = maritalStatusNode ? ("" + maritalStatusNode.value).trim() : "";
    var isSingle = maritalStatus === "Single";
    var isCouple = maritalStatus === "Couple";

    if (!isSingle && !isCouple) {
      console.log("[calculatePug] Invalid or missing 'maritalStatus':", maritalStatus);
      return false;
    }

    var rawIncome = incomeNode ? incomeNode.value : "";
    if (rawIncome === null || rawIncome === "") {
      console.log("[calculatePug] Missing 'income'");
      return false;
    }

    var income = parseFloat("" + rawIncome);
    if (isNaN(income) || income < 0) {
      console.log("[calculatePug] Invalid 'income' value:", rawIncome);
      return false;
    }

    var spouseIncome = 0;
    if (isCouple) {
      var rawSpouseIncome = spouseIncomeNode ? spouseIncomeNode.value : "";
      if (rawSpouseIncome === null || rawSpouseIncome === "") {
        console.log("[calculatePug] Missing 'spouseIncome' for Couple");
        return false;
      }

      spouseIncome = parseFloat("" + rawSpouseIncome);
      if (isNaN(spouseIncome) || spouseIncome < 0) {
        console.log("[calculatePug] Invalid 'spouseIncome' value:", rawSpouseIncome);
        return false;
      }
    }

    var combinedIncome = income + spouseIncome;

    console.log("[calculatePug] location=" + location +
      ", maritalStatus=" + maritalStatus +
      ", income=" + income +
      ", spouseIncome=" + spouseIncome +
      ", combinedIncome=" + combinedIncome);

    if (location === "Rural") {
      if (isSingle && income < 148900 || isCouple && combinedIncome < 210000) {
            guideBridge.setFocus("startPanel");
            guideBridge.setFocus("guide[0].guide1[0].guideRootPanel[0].startPanel[0]");
            return true;
      }
    }

    if (location === "Urban") {
       if (isSingle && income < 148900 || isCouple && combinedIncome < 210000) {
            guideBridge.setFocus("startPanel");
            guideBridge.setFocus("guide[0].guide1[0].guideRootPanel[0].startPanel[0]");
            return true;
       }
    }

    guideBridge.setFocus("ineligiblePanel");
    console.info("[calculatePug] Eligibility conditions not met.");
    return false;

  } catch (e) {
    console.error("[calculatePug] Exception occurred:", e);
    return false;
  }
}
