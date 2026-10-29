package top.aameow.management.controller;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import top.aameow.common.annotation.Log;
import top.aameow.common.enums.BusinessType;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.core.page.TableDataInfo;
import top.aameow.management.service.FinanceLedgerService;

@RestController @RequestMapping("/finance/ledger")
public class FinanceLedgerController extends BaseController {
 @Autowired private FinanceLedgerService service;
 @GetMapping("/overview") @PreAuthorize("@ss.hasPermi('zhongji:finance:list')") public AjaxResult overview(){return success(service.overview());}
 @GetMapping("/{kind:accounts|transactions|invoices|contracts|projects}") @PreAuthorize("@ss.hasPermi('zhongji:finance:list')") public TableDataInfo list(@PathVariable String kind,@RequestParam Map<String,Object> q){service.filter(q);startPage();return getDataTable(switch(kind){case "accounts"->service.data().accounts(q);case "transactions"->service.data().transactions(q);case "invoices"->service.data().invoices(q);case "contracts"->service.data().contracts(q);default->service.data().projects(q);});}
 @GetMapping("/{kind:transactions|invoices}/{id}") @PreAuthorize("@ss.hasPermi('zhongji:finance:list')") public AjaxResult detail(@PathVariable String kind,@PathVariable Long id){return success(kind.equals("invoices")?service.data().invoice(id):service.data().transaction(id));}
 @GetMapping("/options/{kind:contracts|claims}") @PreAuthorize("@ss.hasPermi('zhongji:finance:list')") public AjaxResult options(@PathVariable String kind,@RequestParam(defaultValue="") String name){return success(kind.equals("contracts")?service.data().contractOptions(name.trim()):service.data().claimOptions(name.trim()));}
 @PostMapping("/accounts") @PreAuthorize("@ss.hasPermi('zhongji:finance:ledger')") @Log(title="资金账户",businessType=BusinessType.INSERT) public AjaxResult account(@RequestBody Map<String,Object> v){return success(service.account(v));}
 @PostMapping("/transactions") @PreAuthorize("@ss.hasPermi('zhongji:finance:ledger')") @Log(title="收支流水",businessType=BusinessType.INSERT) public AjaxResult transaction(@RequestBody Map<String,Object> v){return success(service.transaction(v));}
 @PostMapping("/invoices") @PreAuthorize("@ss.hasPermi('zhongji:finance:ledger')") @Log(title="已开发票记录",businessType=BusinessType.INSERT) public AjaxResult invoice(@RequestBody Map<String,Object> v){return success(service.invoice(v));}
 @PutMapping("/{kind:transactions|invoices}/{id}/void") @PreAuthorize("@ss.hasPermi('zhongji:finance:ledger')") @Log(title="财务记录作废",businessType=BusinessType.UPDATE) public AjaxResult cancel(@PathVariable String kind,@PathVariable Long id,@RequestBody Map<String,Object> v){if(kind.equals("invoices"))service.voidInvoice(id,v);else service.voidTransaction(id,v);return success();}
}
