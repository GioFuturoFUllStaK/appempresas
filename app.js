'use strict';
// LOG empresas © Todos os direitos reservados. Código próprio, sem bibliotecas externas.
const $=s=>document.querySelector(s),
E=s=>String(s??'').replace(/[&<>"']/g,c=>'&#'+c.charCodeAt(0)+';'),
PR={CAVACO:'Cavaco',BAGACO:'Bagaço',SERRAGEM:'Serragem',CASCA:'Casca',OUTRO:'Outro'},
L={...PR,CARGA:'Carga',DESCARGA:'Descarga',AGENDADA:'Agendada',EM_ANDAMENTO:'Em andamento',CONCLUIDA:'Concluída',CANCELADA:'Cancelada',PENDENTE:'Pendente',EMITIDA:'Emitida',APROVADO:'Aprovado',REPROVADO:'Reprovado',ENTRADA:'Entrada',INI_INTERVALO:'Início do intervalo',FIM_INTERVALO:'Fim do intervalo',SAIDA:'Saída'},
lb=k=>L[k]||E(k),kg=p=>E(p).replace('.',','),dt=s=>s.slice(8,10)+'/'+s.slice(5,7)+' '+s.slice(11,16),
IT=['Freios e pressão de ar','Pneus e estepe','Luzes e setas','Óleo, água e vazamentos','Documentos (CNH, CRLV, ANTT)','Extintor e triângulo','Piso móvel e cilindros hidráulicos','Mangueiras sem vazamento','Portas e travas traseiras','Lona ou cobertura da carga','Quinta roda, engate e suspensão','Placas e faixas refletivas'],
TABS=[['painel','📊','Painel'],['cargas','🚛','Cargas'],['notas','🧾','Notas'],['check','✅','Checklist'],['ponto','⏱️','Ponto']],
NX={AGENDADA:['EM_ANDAMENTO','Iniciar'],EM_ANDAMENTO:['CONCLUIDA','Concluir']},
NP={'':['ENTRADA'],ENTRADA:['INI_INTERVALO','SAIDA'],INI_INTERVALO:['FIM_INTERVALO'],FIM_INTERVALO:['SAIDA'],SAIDA:['ENTRADA']},
PL='ABC1D23',V={},B={};
let U,D={},T='painel';
async function api(p,d){
 const r=await fetch('/api/'+p,d?{method:'POST',headers:{'X-LOG':'1','Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(d)}:{}),j=await r.json().catch(()=>({}));
 if(r.status===401&&p!=='login'){U=null;entrar('Sessão expirada. Entre novamente.');throw Error('Sessão expirada.')}
 if(!r.ok)throw Error(j.erro||'Falha na operação.');
 return j}
function msg(m,er){const e=document.createElement('div');e.className='tt'+(er?' er':'');e.textContent=m;document.body.append(e);setTimeout(()=>e.remove(),3500)}
const I=(n,t,p='',x='')=>`<label>${t}<input name="${n}" placeholder="${p}" maxlength="60" required ${x}></label>`,
S=(n,t,o)=>`<label>${t}<select name="${n}">${Object.entries(o).map(([k,v])=>`<option value="${k}">${v}</option>`).join('')}</select></label>`,
BD=s=>`<span class="bd ${s}">${lb(s)}</span>`,
VZ=x=>`<p class="vz">${x}</p>`,
env=(col,ex)=>async e=>{e.preventDefault();try{await api(col,{...Object.fromEntries(new FormData(e.target)),...(ex?ex(e.target):{})});msg('Salvo com sucesso.');ir(T)}catch(x){msg(x.message,1)}},
BS=()=>document.querySelectorAll('[data-s]').forEach(b=>b.onclick=async()=>{try{await api(b.dataset.s,{});msg('Status atualizado.');ir(T)}catch(e){msg(e.message,1)}}),
CK=x=>`<label class="ck"><input type="checkbox" class="it"><span>${x}</span></label>`,
CG=c=>{const n=NX[c.status];return `<div class="cd"><div class="lh"><b>#${c.id} ${lb(c.operacao)} de ${lb(c.produto)}</b>${BD(c.status)}</div><div>${E(c.placa)} + ${E(c.carreta)} · ${E(c.motorista)}</div><div>${E(c.origem)} → ${E(c.destino)} · ${kg(c.peso)} t</div><small>${dt(c.criado)}</small>${n?`<div class="ac"><button data-s="cargas/${c.id}/${n[0]}">${n[1]}</button>${c.status==='AGENDADA'?`<button class="cl" data-s="cargas/${c.id}/CANCELADA">Cancelar</button>`:''}</div>`:''}</div>`};
function entrar(m){document.body.className='lg';
 $('#app').innerHTML=`<form class="cd lgc" id="f"><div class="logo big"><b>LOG</b>empresas</div>${VZ('Cargas, notas, checklists e ponto da operação de piso móvel.')}${I('u','Usuário','','autocomplete="username" autofocus')}<label>Senha<input name="s" type="password" required autocomplete="current-password"></label><p class="er" id="er">${E(m||'')}</p><button class="am">Entrar</button></form>`;
 $('#f').onsubmit=async e=>{e.preventDefault();try{U=await api('login',Object.fromEntries(new FormData(e.target)));inicio()}catch(x){$('#er').textContent=x.message}}}
async function inicio(){
 try{U=U||await api('eu')}catch{return entrar()}
 document.body.className='';
 $('#app').innerHTML=`<header class="topo"><div class="logo"><b>LOG</b>empresas</div><span>${E(U.nome)}</span><button class="cl" id="sair">Sair</button></header><nav>${TABS.map(([k,i,n])=>`<button data-t="${k}"><i>${i}</i>${n}</button>`).join('')}</nav><main id="pg"></main>`;
 $('#sair').onclick=async()=>{await api('sair',{});U=null;entrar()};
 document.querySelectorAll('nav button').forEach(b=>b.onclick=()=>ir(b.dataset.t));
 ir(T)}
async function ir(t){T=t;document.querySelectorAll('nav button').forEach(b=>b.classList.toggle('on',b.dataset.t===t));
 try{for(const c of['cargas','notas','checklists','pontos'])D[c]=await api(c)}catch(e){return msg(e.message,1)}
 $('#pg').innerHTML=V[t]();B[t]()}
V.painel=()=>{const c=D.cargas,ab=c.filter(x=>NX[x.status]).length,tn=c.filter(x=>x.status==='CONCLUIDA').reduce((s,x)=>s+parseFloat(x.peso),0),
 k=(n,x)=>`<div class="cd"><div class="n">${n}</div>${x}</div>`;
 return `<h1>Olá, ${E(U.nome)}</h1><div class="gr">${k(ab,'Cargas em aberto')}${k(D.notas.filter(x=>x.status==='PENDENTE').length,'Notas pendentes')}${k(D.checklists.filter(x=>x.status==='REPROVADO').length,'Checklists reprovados')}${k(tn.toFixed(1).replace('.',','),'Toneladas concluídas')}</div><div class="ac"><button class="am" data-g="cargas">Nova carga ou descarga</button><button data-g="check">Fazer checklist</button><button data-g="ponto">Registrar ponto</button></div><h2>Últimas cargas</h2>${c.slice(0,5).map(CG).join('')||VZ('Nenhuma carga ainda. Toque em “Nova carga ou descarga”.')}`};
B.painel=()=>{document.querySelectorAll('[data-g]').forEach(b=>b.onclick=()=>ir(b.dataset.g));BS()};
V.cargas=()=>`<h1>Cargas e descargas</h1><form class="cd" id="f"><h2>Nova operação</h2>${S('operacao','Operação',{CARGA:'Carga',DESCARGA:'Descarga'})}${S('produto','Produto',PR)}${I('placa','Placa do caminhão',PL)}${I('carreta','Placa da carreta (piso móvel)',PL)}${I('motorista','Motorista')}${I('origem','Origem','Ex.: Fazenda Boa Vista')}${I('destino','Destino','Ex.: Usina ou caldeira')}${I('peso','Peso (toneladas)','Ex.: 24,5','inputmode="decimal"')}<button class="am">Salvar operação</button></form><h2>Registradas</h2>${D.cargas.map(CG).join('')||VZ('Nenhuma operação registrada.')}`;
B.cargas=()=>{$('#f').onsubmit=env('cargas');BS()};
V.notas=()=>{const op=D.cargas.filter(c=>c.status!=='CANCELADA');
 return `<h1>Solicitação de nota fiscal</h1><form class="cd" id="f"><h2>Solicitar nota</h2><label>Carga<select name="carga">${op.map(c=>`<option value="${c.id}" data-p="${E(c.produto)}" data-w="${E(c.peso)}">#${c.id} · ${lb(c.produto)} · ${E(c.placa)} → ${E(c.destino)}</option>`).join('')}</select></label>${I('cliente','Cliente ou destinatário')}<button class="am" ${op.length?'':'disabled'}>Solicitar nota</button>${op.length?'':VZ('Cadastre uma carga antes de pedir a nota.')}</form><h2>Pedidos</h2>${D.notas.map(n=>`<div class="cd"><div class="lh"><b>Nota da carga #${E(n.carga)}</b>${BD(n.status)}</div><div>${E(n.cliente)} · ${lb(n.produto)} · ${kg(n.peso)} t</div><small>${dt(n.criado)} · pedido por ${E(n.usuario)}</small>${U.perfil==='ADMIN'&&n.status==='PENDENTE'?`<div class="ac"><button data-s="notas/${n.id}/EMITIDA">Marcar como emitida</button><button class="cl" data-s="notas/${n.id}/CANCELADA">Cancelar</button></div>`:''}</div>`).join('')||VZ('Nenhum pedido de nota.')}`};
B.notas=()=>{$('#f').onsubmit=env('notas',fm=>{const o=fm.carga.selectedOptions[0];return{produto:o.dataset.p,peso:o.dataset.w}});BS()};
V.check=()=>`<h1>Checklist de saída</h1><form class="cd" id="f">${I('placa','Placa do caminhão',PL)}${I('carreta','Placa da carreta',PL)}${I('motorista','Motorista')}${VZ('Marque cada item que está conforme. Item sem marca reprova o checklist.')}<h2>Caminhão</h2>${IT.slice(0,6).map(CK).join('')}<h2>Carreta de piso móvel</h2>${IT.slice(6).map(CK).join('')}<label>Observações<textarea name="obs" maxlength="200" rows="2"></textarea></label><button class="am">Enviar checklist</button></form><h2>Últimos checklists</h2>${D.checklists.map(c=>`<div class="cd"><div class="lh"><b>${E(c.placa)} + ${E(c.carreta)}</b>${BD(c.status)}</div><div>${E(c.motorista)}</div>${[...c.itens].map((v,i)=>v==='0'?`<div class="pb">Problema: ${IT[i]}</div>`:'').join('')}<small>${dt(c.criado)}</small></div>`).join('')||VZ('Nenhum checklist enviado.')}`;
B.check=()=>{$('#f').onsubmit=env('checklists',fm=>({itens:[...fm.querySelectorAll('.it')].map(c=>c.checked?1:0).join('')}))};
V.ponto=()=>{const me=D.pontos.find(p=>p.usuario===U.nome),ok=NP[me?me.tipo:''];
 return `<h1>Registro de ponto</h1><div class="cd"><div class="n" id="rel"></div><small>O horário gravado é o do servidor, no momento do toque.</small><div class="gr bp">${['ENTRADA','INI_INTERVALO','FIM_INTERVALO','SAIDA'].map(k=>`<button class="${ok.includes(k)?'am':''}" data-p="${k}" ${ok.includes(k)?'':'disabled'}>${lb(k)}</button>`).join('')}</div></div><h2>Registros recentes</h2>${D.pontos.slice(0,30).map(p=>`<div class="cd lh"><b>${lb(p.tipo)}</b><span>${U.perfil==='ADMIN'?E(p.usuario)+' · ':''}${dt(p.criado)}</span></div>`).join('')||VZ('Nenhum registro ainda.')}`};
B.ponto=()=>document.querySelectorAll('[data-p]').forEach(b=>b.onclick=async()=>{try{await api('pontos',{tipo:b.dataset.p});msg('Ponto registrado: '+lb(b.dataset.p)+'.');ir(T)}catch(e){msg(e.message,1)}});
setInterval(()=>{const r=$('#rel');if(r)r.textContent=new Date().toLocaleTimeString('pt-BR')},1000);
inicio();
