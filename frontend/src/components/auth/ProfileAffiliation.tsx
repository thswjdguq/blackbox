"use client";
import {useEffect,useState} from "react";
import AffiliationFields,{emptyAffiliation,affiliationPayload,affiliationReady} from "./AffiliationFields";
import {CurrentProfile} from "@/lib/store/profileStore";
import api from "@/lib/api";
import {apiError} from "@/lib/apiError";
import {sessionGeneration} from "@/lib/sessionCache";
export default function ProfileAffiliation({profile,onSaved}:{profile:CurrentProfile;onSaved:(p:CurrentProfile)=>void}) {
  const [value,setValue]=useState(emptyAffiliation),[editing,setEditing]=useState(false),[saving,setSaving]=useState(false),[message,setMessage]=useState("");
  useEffect(()=>{const a=profile.affiliation;setValue(a?{source:a.source,schoolId:a.schoolId??"",departmentId:a.departmentId??"",schoolName:a.schoolName,departmentName:a.departmentName}:emptyAffiliation());},[profile]);
  const save=async()=>{if(saving||!affiliationReady(value))return;const generation=sessionGeneration();setSaving(true);setMessage("");
    try{const {data}=await api.put<CurrentProfile>("/auth/profile",{name:profile.name,affiliation:affiliationPayload(value)});if(generation===sessionGeneration()){onSaved(data);setEditing(false);setMessage("소속 정보를 저장했습니다.");}}
    catch(e){if(generation===sessionGeneration())setMessage(apiError(e,"소속 정보를 저장하지 못했습니다."));}
    finally{if(generation===sessionGeneration())setSaving(false);}
  };
  return <div className="space-y-3">
    <p className="text-sm text-bb-text">{profile.affiliation?`${profile.affiliation.schoolName} · ${profile.affiliation.departmentName}`:"등록된 소속이 없습니다."}</p>
    {profile.affiliation&&<p className="text-xs text-bb-text2">{profile.affiliation.source==="CATALOG"?`공식 목록 선택 · ${profile.affiliation.catalogYear}년 자료`:"사용자 입력"} · 재학 인증 아님</p>}
    {editing?<><AffiliationFields value={value} onChange={setValue} disabled={saving}/><div className="flex gap-3"><button type="button" onClick={save} disabled={saving||!affiliationReady(value)} className="rounded-lg bg-bb-primary px-4 py-2 text-sm text-white disabled:opacity-50">{saving?"저장 중…":"소속 저장"}</button><button type="button" disabled={saving} onClick={()=>{setEditing(false);setMessage("");}} className="text-sm text-bb-text2">취소</button></div></>:<button type="button" onClick={()=>setEditing(true)} className="text-sm text-bb-primary underline">{profile.affiliation?"소속 수정":"소속 추가"}</button>}
    {message&&<p role="status" className="text-sm text-bb-text2">{message}</p>}
  </div>;
}
