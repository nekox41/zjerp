# coding: utf-8
from pathlib import Path
p=Path('zjerp_frontend-master/src/views/zhongji/components/ContractWorkspace.vue');s=p.read_text(encoding='utf-8')
s=s.replace("let snapshot='',sequence=0;","let snapshot='',sequence=0,originalAmount=null,originalAmountInput='';")
s=s.replace("function create(){form.value=", "function create(){originalAmount=null;originalAmountInput='';form.value=")
s=s.replace("form.value=r.data;plans.value=", "form.value=r.data;originalAmount=form.value.amount;originalAmountInput=originalAmount===null?null:Number(originalAmount).toFixed(2);form.value.amount=originalAmountInput;plans.value=")
s=s.replace("if(form.value.amount!==null && !validAmount(form.value.amount) && !form.value.id)","if(form.value.amount!==null && !validAmount(form.value.amount))")
s=s.replace("contractType:props.contractType,paymentPlan:JSON.stringify(plans.value)","contractType:props.contractType,amount:form.value.id&&form.value.amount===originalAmountInput?originalAmount:form.value.amount,paymentPlan:JSON.stringify(plans.value)")
p.write_text(s,encoding='utf-8')
p=Path('zjerp_backend-master/zjerp-management/src/main/resources/mapper/management/ZjContractMapper.xml');s=p.read_text(encoding='utf-8').replace('<if test="amount != null">amount=#{amount},</if>','amount=#{amount},');p.write_text(s,encoding='utf-8')
