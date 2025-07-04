window.calculateEligibility = function(data) {
  console.log("[checkPugEligibility] Starting validation with data:", data);

  const location = (data.location || "").trim();
  if (location !== "Urban" && location !== "Rural") {
    console.log("[checkPugEligibility] Invalid or missing 'location':", location);
    return false;
  }

  const maritalStatus = (data.maritalStatus || "").trim();
  const isSingle = maritalStatus === "Single";
  const isCouple = maritalStatus === "Couple";

  if (!isSingle && !isCouple) {
    console.log("[checkPugEligibility] Invalid or missing 'maritalStatus':", maritalStatus);
    return false;
  }

  const rawIncome = data.income;
  const rawSpouseIncome = data.spouseIncome;

  if (rawIncome === undefined || rawIncome === null || rawIncome === "") {
    console.log("[checkPugEligibility] Missing 'income'");
    return false;
  }

  const income = parseFloat("" + rawIncome);
  if ("" + income === "NaN" || income < 0) {
    console.log("[checkPugEligibility] Invalid 'income' value:", rawIncome);
    return false;
  }

  let spouseIncome = 0;
  if (isCouple) {
    if (rawSpouseIncome === undefined || rawSpouseIncome === null || rawSpouseIncome === "") {
      console.log("[checkPugEligibility] Missing 'spouseIncome' for Couple");
      return false;
    }

    spouseIncome = parseFloat("" + rawSpouseIncome);
    if ("" + spouseIncome === "NaN" || spouseIncome < 0) {
      console.log("[checkPugEligibility] Invalid 'spouseIncome' value:", rawSpouseIncome);
      return false;
    }
  }

  const combinedIncome = income + spouseIncome;
  console.log(`[checkPugEligibility] location=${location}, maritalStatus=${maritalStatus}, income=${income}, spouseIncome=${spouseIncome}, combinedIncome=${combinedIncome}`);

  if (location === "Rural") {
    if (isSingle && income < 148900) return true;
    if (isCouple && combinedIncome < 210000) return true;
  }

  if (location === "Urban") {
    if (isSingle && income < 148900) return true;
    if (isCouple && combinedIncome < 210000) return true;
  }

  console.info("[checkPugEligibility] Eligibility conditions not met.");
  return false;
};

document.addEventListener("guideLoaded", function () {
  console.log("[Eligibility] guideLoaded");

  const buttonNode = guideBridge.resolveNode("checkEligibilityButton");
  if (!buttonNode) {
    console.warn("Button not found: checkEligibilityButton");
    return;
  }

  buttonNode.element.addEventListener("click", function () {
    console.log("[Eligibility] Button clicked");

    const data = {
      location: guideBridge.resolveNode("location")?.value,
      maritalStatus: guideBridge.resolveNode("maritalStatus")?.value,
      income: guideBridge.resolveNode("income")?.value,
      spouseIncome: guideBridge.resolveNode("spouseIncome")?.value
    };

    const isEligible = window.calculateEligibility(data);

    if (isEligible) {
      console.log("Eligible – navigating to next panel");
      guideBridge.nextPanel();
    } else {
      console.warn("Not eligible");

      const errorField = guideBridge.resolveNode("eligibilityResult");
      if (errorField) {
        errorField.value = "You are not eligible for this application.";
      } else {
        alert("You are not eligible for this application.");
      }
    }
  });
});
