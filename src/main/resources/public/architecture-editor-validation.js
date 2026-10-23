/* Shared editor input primitives. Calendar validation only; no assessment or risk matrix. */
(() => {
  const normalize=value=>String(value??"").replace(/\r\n?/g,"\n").normalize("NFC").trim()||null;
  function validDate(value) {
    if(value===null)return true;
    if(!/^\d{4}-\d{2}-\d{2}$/.test(value))return false;
    const [y,m,d]=value.split('-').map(Number),leap=y%4===0&&(y%100!==0||y%400===0);
    return m>=1&&m<=12&&d>=1&&d<=[31,leap?29:28,31,30,31,30,31,31,30,31,30,31][m-1];
  }
  window.PMArchitectureEditorValidation={normalize,validDate};
})();
