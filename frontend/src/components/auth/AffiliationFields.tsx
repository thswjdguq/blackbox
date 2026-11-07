"use client";
import { useEffect, useId, useMemo, useState } from "react";
import api from "@/lib/api";

export interface AffiliationInput { source: "CATALOG" | "SELF_REPORTED"; schoolId: string; departmentId: string; schoolName: string; departmentName: string }
export const emptyAffiliation = (): AffiliationInput => ({source:"CATALOG",schoolId:"",departmentId:"",schoolName:"",departmentName:""});
export function affiliationPayload(value: AffiliationInput) {
  return value.source === "CATALOG" ? {source:value.source,schoolId:value.schoolId,departmentId:value.departmentId}
    : {source:value.source,schoolName:value.schoolName.trim(),departmentName:value.departmentName.trim()};
}
export function affiliationReady(value: AffiliationInput) { return value.source === "CATALOG" ? !!value.schoolId && !!value.departmentId : !!value.schoolName.trim() && !!value.departmentName.trim(); }
interface School { id:string; name:string; region:string; type:string }
interface Department { id:string; name:string }
const input="w-full rounded-lg border border-bb-border bg-bb-surface px-3 py-2.5 text-sm text-bb-text disabled:opacity-50";
export default function AffiliationFields({value,onChange,disabled=false}:{value:AffiliationInput;onChange:(value:AffiliationInput)=>void;disabled?:boolean}) {
  const id=useId();
  const [schools,setSchools]=useState<School[]>([]),[departments,setDepartments]=useState<Department[]>([]);
  const [query,setQuery]=useState(""),[year,setYear]=useState<number>(),[asOf,setAsOf]=useState("");
  const [loading,setLoading]=useState(true),[departmentLoading,setDepartmentLoading]=useState(false);
  const [error,setError]=useState(""),[departmentError,setDepartmentError]=useState(""),[retry,setRetry]=useState(0);
  useEffect(()=>{let active=true;setLoading(true);setError("");
    api.get<{year:number;asOf:string;schools:School[]}>("/auth/affiliation/schools").then(({data})=>{if(active){setSchools(data.schools);setYear(data.year);setAsOf(data.asOf);}})
      .catch(()=>{if(active)setError("학교 목록을 불러오지 못했습니다. 다시 시도하거나 직접 입력해 주세요.");}).finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[retry]);
  useEffect(()=>{let active=true;setDepartments([]);setDepartmentError("");
    if(value.source!=="CATALOG"||!value.schoolId){setDepartmentLoading(false);return;}
    setDepartmentLoading(true);
    api.get<Department[]>(`/auth/affiliation/schools/${encodeURIComponent(value.schoolId)}/departments`).then(({data})=>{if(active)setDepartments(data);})
      .catch(()=>{if(active)setDepartmentError("학과 목록을 불러오지 못했습니다. 다시 시도하거나 직접 입력해 주세요.");}).finally(()=>{if(active)setDepartmentLoading(false);});
    return()=>{active=false;};
  },[value.schoolId,value.source,retry]);
  const filtered=useMemo(()=>schools.filter(s=>s.id===value.schoolId||`${s.name} ${s.region}`.replaceAll(" ","").includes(query.replaceAll(" ",""))),[schools,query,value.schoolId]);
  return <fieldset disabled={disabled} className="space-y-3">
    <legend className="mb-2 text-sm font-medium text-bb-text">소속 대학교·학과</legend>
    <div className="flex flex-wrap gap-4 text-sm text-bb-text">
      <label className="flex items-center gap-2"><input type="radio" name={`${id}-mode`} checked={value.source==="CATALOG"} onChange={()=>onChange(emptyAffiliation())}/>목록에서 선택</label>
      <label className="flex items-center gap-2"><input type="radio" name={`${id}-mode`} checked={value.source==="SELF_REPORTED"} onChange={()=>onChange({...value,source:"SELF_REPORTED",schoolId:"",departmentId:""})}/>직접 입력</label>
    </div>
    {value.source==="CATALOG" ? <>
      <div><label htmlFor={`${id}-search`} className="mb-1 block text-xs text-bb-text2">학교 검색</label><input id={`${id}-search`} type="search" value={query} onChange={e=>setQuery(e.target.value)} placeholder="학교명 또는 지역으로 검색" className={input}/></div>
      <div><label htmlFor={`${id}-school`} className="mb-1 block text-xs text-bb-text2">대학교</label><select id={`${id}-school`} required value={value.schoolId} disabled={loading||!!error} onChange={e=>{const school=schools.find(s=>s.id===e.target.value);onChange({...emptyAffiliation(),schoolId:school?.id??"",schoolName:school?.name??""});}} className={input}>
        <option value="">{loading?"학교 목록을 불러오는 중…":filtered.length?"학교를 선택해 주세요":"검색 결과가 없습니다 — 직접 입력을 이용해 주세요"}</option>
        {filtered.map(s=><option key={s.id} value={s.id}>{s.name} · {s.region} · {s.type}</option>)}
      </select></div>
      <div><label htmlFor={`${id}-department`} className="mb-1 block text-xs text-bb-text2">학과·전공</label><select id={`${id}-department`} required value={value.departmentId} disabled={!value.schoolId||departmentLoading||!!departmentError} onChange={e=>onChange({...value,departmentId:e.target.value,departmentName:departments.find(d=>d.id===e.target.value)?.name??""})} className={input}>
        <option value="">{departmentLoading?"학과 목록을 불러오는 중…":value.schoolId?"학과를 선택해 주세요":"학교를 먼저 선택해 주세요"}</option>
        {departments.map(d=><option key={d.id} value={d.id}>{d.name}</option>)}
      </select></div>
      {(error||departmentError)&&<p role="alert" className="text-xs text-red-600">{error||departmentError} <button type="button" onClick={()=>setRetry(n=>n+1)} className="underline">다시 불러오기</button></p>}
      {year&&<p className="text-xs text-bb-text2">공식 자료 {year}년 · 데이터 기준 {asOf}. 학교·학과가 없거나 변경됐다면 직접 입력해 주세요.</p>}
    </> : <>
      <div><label htmlFor={`${id}-manual-school`} className="mb-1 block text-xs text-bb-text2">대학교명</label><input id={`${id}-manual-school`} required maxLength={150} value={value.schoolName} onChange={e=>onChange({...value,schoolName:e.target.value})} placeholder="소속 대학교의 정식 명칭" className={input}/></div>
      <div><label htmlFor={`${id}-manual-department`} className="mb-1 block text-xs text-bb-text2">학과·전공명</label><input id={`${id}-manual-department`} required maxLength={150} value={value.departmentName} onChange={e=>onChange({...value,departmentName:e.target.value})} placeholder="현재 소속 학과·전공" className={input}/></div>
      <p className="text-xs text-bb-text2">직접 입력한 소속은 ‘사용자 입력’으로 저장됩니다. 학교 목록과 대조된 정보가 아닙니다.</p>
    </>}
    <p className="text-xs text-bb-text2">소속 정보 입력은 재학 인증이 아닙니다.</p>
  </fieldset>;
}
