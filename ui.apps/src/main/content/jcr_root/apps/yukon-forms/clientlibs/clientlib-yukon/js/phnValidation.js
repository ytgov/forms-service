/**
 * Validates the PHN
 * @name validatePHN YT Personal Health Number Validation
 */

// Main function
// Get and validate input from user
function validatePHN(phn) {
    phn = phn.toString();

    // check if the number has 9 digits
    if (/^\d{9}$/.test(phn)) {

        // Step1: Double the even digits
        // Store the resulted number in step1Result
        var step1Result = "";

        for (var i = 0; i < phn.length; i++) {
            var c = phn[i];
            // if the last digit is even, then double its value, else keep the original value
            step1Result += ((i + 1) % 2 === 0) ? String(Number(c) * 2) : c;
        }


        // Step2: Add up all the numbers from step 1 but treat double digits as two separate digits
        // treat double digits as two separate digits - not relevant as 1 + (2 + 1) = 1 + 2 + 1
        // Add up all the digits
        var step2Total = 0;

        for (var i = 0; i < step1Result.length; i++) {
            step2Total += Number(step1Result[i]);
        }

        // Step3: Divide the result from step2 by 10
        // boolean
        // if the result is a whole number (no decimals) then the example is valid
        return step2Total > 0 && step2Total % 10 === 0;

    }

    return false;
}
