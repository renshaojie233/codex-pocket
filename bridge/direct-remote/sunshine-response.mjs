export function sunshineResponseSucceeded(result) {
  const status = result?.status;
  return status === true || (typeof status === "string" && status.toLowerCase() === "true");
}
